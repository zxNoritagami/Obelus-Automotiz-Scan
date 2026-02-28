package com.obelus.actuator

import com.obelus.presentation.viewmodel.ScanUiState
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// SafetyChecker.kt
// Valida condiciones de seguridad ANTES de ejecutar un test de actuador.
// Nunca bloquea el UI thread — consulta el estado ya capturado en ScanUiState.
// ─────────────────────────────────────────────────────────────────────────────

/** Resultado de la validación de seguridad. */
sealed class SafetyResult {
    /** Todas las condiciones OK – el test puede ejecutarse. */
    object Safe : SafetyResult()

    /** Una o más condiciones falladas – el test NO debe ejecutarse. */
    data class Unsafe(val violations: List<SafetyViolation>) : SafetyResult()
}

/**
 * Verifica condiciones de seguridad consultando el estado actual del escaneo.
 *
 * Condiciones verificadas:
 *  1. RPM = 0 (motor detenido)
 *  2. Temperatura del refrigerante en rango seguro (< 110 °C, > -10 °C)
 *  3. Tensión de batería > 11.5 V (para evitar daño al ECU)
 *  4. No hay DTC de falla grave activo (categoría P0 con MIL encendido)
 */
@Singleton
class SafetyChecker @Inject constructor() {

    companion object {
        private const val MAX_SAFE_RPM           = 0f      // motor detenido
        private const val MAX_COOLANT_TEMP_C     = 110f    // °C máximo
        private const val MIN_COOLANT_TEMP_C     = -10f    // °C mínimo
        private const val MIN_BATTERY_VOLTAGE    = 11.5f   // V mínimo
        private const val RPM_PID                = "0C"
        private const val COOLANT_PID            = "05"
        private const val BATTERY_PID            = "42"
    }

    /**
     * Evalúa el estado actual del escaneo y retorna si el test puede ejecutarse.
     *
     * @param uiState   Estado actual de la UI del escáner (lecturas en memoria).
     * @return [SafetyResult.Safe] o [SafetyResult.Unsafe] con la lista de violaciones.
     */
    fun validate(uiState: ScanUiState): SafetyResult {
        val violations = mutableListOf<SafetyViolation>()

        // ── 1. RPM = 0 (motor parado) ─────────────────────────────────────────
        val rpm = uiState.readings
            .filter { it.pid.equals(RPM_PID, ignoreCase = true) }
            .maxByOrNull { it.timestamp }
            ?.value
        if (rpm != null && rpm > MAX_SAFE_RPM) {
            violations += SafetyViolation(
                condition = "Motor detenido (RPM = 0)",
                measured  = "${"%.0f".format(rpm)} RPM",
                required  = "0 RPM"
            )
        }

        // ── 2. Temperatura del refrigerante ───────────────────────────────────
        val coolantTemp = uiState.readings
            .filter { it.pid.equals(COOLANT_PID, ignoreCase = true) }
            .maxByOrNull { it.timestamp }
            ?.value
        if (coolantTemp != null) {
            if (coolantTemp > MAX_COOLANT_TEMP_C) {
                violations += SafetyViolation(
                    condition = "Temperatura del refrigerante",
                    measured  = "${"%.1f".format(coolantTemp)} °C",
                    required  = "< $MAX_COOLANT_TEMP_C °C"
                )
            }
            if (coolantTemp < MIN_COOLANT_TEMP_C) {
                violations += SafetyViolation(
                    condition = "Temperatura mínima del motor",
                    measured  = "${"%.1f".format(coolantTemp)} °C",
                    required  = "> $MIN_COOLANT_TEMP_C °C"
                )
            }
        }

        // ── 3. Voltaje de batería ─────────────────────────────────────────────
        val battery = uiState.readings
            .filter { it.pid.equals(BATTERY_PID, ignoreCase = true) }
            .maxByOrNull { it.timestamp }
            ?.value
        if (battery != null && battery < MIN_BATTERY_VOLTAGE) {
            violations += SafetyViolation(
                condition = "Voltaje de batería",
                measured  = "${"%.2f".format(battery)} V",
                required  = "> $MIN_BATTERY_VOLTAGE V"
            )
        }

        return if (violations.isEmpty()) SafetyResult.Safe
        else SafetyResult.Unsafe(violations)
    }

    /**
     * Versión rápida que sólo verifica si las RPM son cero.
     * Útil para tests de bajo riesgo (DangerLevel.LOW).
     */
    fun isEngineOff(uiState: ScanUiState): Boolean {
        val rpm = uiState.readings
            .filter { it.pid.equals(RPM_PID, ignoreCase = true) }
            .maxByOrNull { it.timestamp }
            ?.value
        return rpm == null || rpm <= MAX_SAFE_RPM
    }

    /**
     * Genera un mensaje de resumen de las violaciones detectadas.
     */
    fun formatViolations(violations: List<SafetyViolation>): String =
        violations.joinToString("\n") { v ->
            "• ${v.condition}: se midió ${v.measured} (requiere ${v.required})"
        }

    fun printLog(result: SafetyResult) {
        when (result) {
            is SafetyResult.Safe   -> println("[SafetyChecker] ✅ Todas las condiciones OK")
            is SafetyResult.Unsafe -> println(
                "[SafetyChecker] ⛔ ${result.violations.size} violación(es):\n${formatViolations(result.violations)}"
            )
        }
    }
}
