package com.obelus.freezeframe

import com.obelus.domain.model.FreezeFrameData

// ─────────────────────────────────────────────────────────────────────────────
// FreezeFrameAnalyzer.kt
// Análisis de condiciones anómalas e inferencia de causas probables.
// Las funciones son puras (sin estado, sin IO) — ejecutar en Dispatchers.Default.
// ─────────────────────────────────────────────────────────────────────────────

/** Un hallazgo de condición anómala detectada en el freeze frame. */
data class ConditionFinding(
    val title: String,
    val explanation: String,
    val severity: FindingSeverity,
    /** Parámetros relacionados a esta condición. */
    val relatedParams: List<String> = emptyList()
)

enum class FindingSeverity { INFO, WARNING, CRITICAL }

object FreezeFrameAnalyzer {

    // ═══════════════════════════════════════════════════════════════════════════
    // analyzeConditions — Detectar condiciones anómalas
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Analiza los valores del freeze frame y retorna condiciones detectadas.
     *
     * Reglas implementadas:
     *  1. Motor frío + alta carga          → posible falso positivo / cold-start issue
     *  2. RPM alto + acelerador bajo       → desaceleración brusca o corte de inyección
     *  3. Temp normal + trim fuera de rango → fuga de vacío / sensor MAF contaminado
     *  4. Temperatura excesiva             → posible sobrecalentamiento
     *  5. Trim positivo alto               → mezcla pobre (fuga, MAF, injector débil)
     *  6. Trim negativo alto               → mezcla rica (inyector que gotea, sensor O2)
     *  7. O2 fijo (no oscila)              → catalizador o sensor O2 defectuoso
     *  8. Velocidad cero + RPM > 0         → fallo con motor al ralentí (parked)
     *  9. Voltaje bajo                     → problema eléctrico / batería
     * 10. MAP alta + carga baja            → posible sensor MAP defectuoso
     */
    fun analyzeConditions(data: FreezeFrameData): List<ConditionFinding> {
        val findings = mutableListOf<ConditionFinding>()

        // 1. Motor frío + alta carga
        val coldEngine = data.coolantTemp != null && data.coolantTemp < 40f
        val highLoad   = data.engineLoad != null  && data.engineLoad > 70f
        if (coldEngine && highLoad) {
            findings += ConditionFinding(
                title       = "Motor frío con alta carga",
                explanation = "El motor no había alcanzado temperatura de operación (${data.coolantTemp}°C) " +
                              "pero la carga era alta (${data.engineLoad}%). " +
                              "Puede ser un falso positivo por arranque en frío o conducción agresiva inmediata.",
                severity    = FindingSeverity.WARNING,
                relatedParams = listOf("coolantTemp", "engineLoad")
            )
        }

        // 2. RPM alta + mariposa baja → desaceleración brusca
        val highRpm = data.rpm != null && data.rpm > 2500f
        val lowThrottle = data.throttlePosition != null && data.throttlePosition < 5f
        if (highRpm && lowThrottle) {
            findings += ConditionFinding(
                title       = "Desaceleración brusca detectada",
                explanation = "RPM alta (${"%.0f".format(data.rpm)} rpm) con mariposa casi cerrada " +
                              "(${"%.1f".format(data.throttlePosition)}%). " +
                              "El fallo ocurrió durante levantamiento de pedal — puede ser corte de inyección o error de decel.",
                severity    = FindingSeverity.INFO,
                relatedParams = listOf("rpm", "throttlePosition")
            )
        }

        // 3. Temperatura normal + trim fuera de rango → vacío o MAF
        val normalTemp = data.coolantTemp != null && data.coolantTemp in 75f..105f
        val highTrim   = data.fuelTrimShort != null && kotlin.math.abs(data.fuelTrimShort) > 15f
        val longHigh   = data.fuelTrimLong  != null && kotlin.math.abs(data.fuelTrimLong)  > 20f
        if (normalTemp && (highTrim || longHigh)) {
            val dir    = if ((data.fuelTrimShort ?: 0f) > 0f) "positivo (mezcla pobre)" else "negativo (mezcla rica)"
            findings += ConditionFinding(
                title       = "Corrección de mezcla fuera de rango con motor caliente",
                explanation = "El trim de combustible era $dir (${"%.1f".format(data.fuelTrimShort)}% corto, " +
                              "${"%.1f".format(data.fuelTrimLong)}% largo) con motor a temperatura. " +
                              "Verificar: fugas de vacío, sensor MAF, inyectores.",
                severity    = FindingSeverity.CRITICAL,
                relatedParams = listOf("fuelTrimShort", "fuelTrimLong")
            )
        }

        // 4. Temperatura excesiva
        if (data.coolantTemp != null && data.coolantTemp > 110f) {
            findings += ConditionFinding(
                title       = "Temperatura del refrigerante excesiva",
                explanation = "La ECU registró ${data.coolantTemp}°C al momento del fallo. " +
                              "Riesgo de sobrecalentamiento. Verificar nivel de refrigerante, termostato y bomba de agua.",
                severity    = FindingSeverity.CRITICAL,
                relatedParams = listOf("coolantTemp")
            )
        }

        // 5 & 6. Trim positivo/negativo alto
        val stfHigh = data.fuelTrimShort != null && data.fuelTrimShort > 20f
        val stfLow  = data.fuelTrimShort != null && data.fuelTrimShort < -20f
        if (stfHigh) {
            findings += ConditionFinding(
                title = "Mezcla pobre persistente (trim positivo alto)",
                explanation = "Trim a corto plazo: ${"%.1f".format(data.fuelTrimShort)}%. " +
                              "El motor intenta enriquecer la mezcla. Causas: fuga de vacío, inyector débil, MAF sucio, sensor O2 lento.",
                severity = FindingSeverity.CRITICAL,
                relatedParams = listOf("fuelTrimShort")
            )
        }
        if (stfLow) {
            findings += ConditionFinding(
                title = "Mezcla rica persistente (trim negativo alto)",
                explanation = "Trim a corto plazo: ${"%.1f".format(data.fuelTrimShort)}%. " +
                              "El motor intenta empobrecer la mezcla. Causas: inyector que gotea, sensor O2 sesgado, presión excesiva de combustible.",
                severity = FindingSeverity.CRITICAL,
                relatedParams = listOf("fuelTrimShort")
            )
        }

        // 7. O2 fijo (valor único, sin oscilación posible sin histórico → valor extremo fijo)
        val o2Fixed = data.o2SensorBank1 != null &&
                      (data.o2SensorBank1 < 0.05f || data.o2SensorBank1 > 0.90f)
        if (o2Fixed) {
            findings += ConditionFinding(
                title = "Sensor O2 en valor extremo fijo",
                explanation = "O2 banco 1: ${"%.3f".format(data.o2SensorBank1)} V. " +
                              "Un sensor sano oscila entre 0.1–0.9V activamente. Valor extremo indica sensor defectuoso o catalizador saturado.",
                severity = FindingSeverity.CRITICAL,
                relatedParams = listOf("o2SensorBank1")
            )
        }

        // 8. Vehículo parado + motor en marcha
        val vehicleStopped = data.vehicleSpeed != null && data.vehicleSpeed < 3f
        val engineRunning  = data.rpm != null && data.rpm > 500f
        if (vehicleStopped && engineRunning) {
            findings += ConditionFinding(
                title = "Fallo con vehículo estacionado (ralentí)",
                explanation = "Velocidad: ${"%.0f".format(data.vehicleSpeed)} km/h. " +
                              "El fallo se generó con el motor en marcha pero el vehículo detenido. Posibles causas en electrónica o sensores de emisiones.",
                severity = FindingSeverity.INFO,
                relatedParams = listOf("vehicleSpeed", "rpm")
            )
        }

        // 9. Voltaje bajo
        if (data.batteryVoltage != null && data.batteryVoltage < 11.5f) {
            findings += ConditionFinding(
                title = "Voltaje del sistema eléctrico bajo",
                explanation = "Voltaje: ${"%.2f".format(data.batteryVoltage)} V al momento del fallo. " +
                              "Voltaje bajo puede causar fallos fantasma en módulos electrónicos.",
                severity = FindingSeverity.WARNING,
                relatedParams = listOf("batteryVoltage")
            )
        }

        // 10. MAP alta + carga baja
        if (data.intakePressure != null && data.engineLoad != null &&
            data.intakePressure > 90f && data.engineLoad < 30f) {
            findings += ConditionFinding(
                title = "Presión MAP alta con carga baja",
                explanation = "MAP: ${"%.0f".format(data.intakePressure)} kPa con carga ${"%.0f".format(data.engineLoad)}%. " +
                              "Inconsistencia — posible sensor MAP defectuoso o fuga en el colector.",
                severity = FindingSeverity.WARNING,
                relatedParams = listOf("intakePressure", "engineLoad")
            )
        }

        return findings
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // suggestRelatedDtcs — Inferir DTCs que se deberían verificar
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sugiere DTCs adicionales a verificar en base al DTC primario y las condiciones del freeze frame.
     *
     * @param primaryDtc  DTC que disparó el freeze frame (ej. "P0420").
     * @param data        Datos del freeze frame.
     * @return Lista de DTC codes sugeridos (sin el primario).
     */
    fun suggestRelatedDtcs(primaryDtc: String, data: FreezeFrameData): List<String> {
        val suggestions = mutableSetOf<String>()
        val trimHigh = data.fuelTrimShort?.let { kotlin.math.abs(it) > 15f } ?: false
        val trimRich = data.fuelTrimShort?.let { it < -15f } ?: false
        val trimLean = data.fuelTrimShort?.let { it > 15f } ?: false

        when {
            primaryDtc.startsWith("P04") -> {
                // Emisiones — catalizador / EVAP
                suggestions += "P0136"; suggestions += "P0141"   // O2 downstream
                suggestions += "P0440"; suggestions += "P0446"   // EVAP
            }
            primaryDtc.startsWith("P03") -> {
                // Misfire
                suggestions += "P0301"; suggestions += "P0302"
                suggestions += "P0303"; suggestions += "P0304"
                if (trimLean) suggestions += "P0171"             // Lean bank 1
            }
            primaryDtc.startsWith("P01") -> {
                // Combustible
                if (trimHigh) { suggestions += "P0171"; suggestions += "P0174" }
                if (trimRich) { suggestions += "P0172"; suggestions += "P0175" }
            }
        }

        // Condición de temp alta → sugerir coolant related
        if (data.coolantTemp != null && data.coolantTemp > 108f) {
            suggestions += "P0217"   // Engine Overtemperature
            suggestions += "P0128"   // Thermostat
        }

        // Voltaje bajo → verificar alternador y sensores
        if (data.batteryVoltage != null && data.batteryVoltage < 11.8f) {
            suggestions += "P0562"   // System Voltage Low
        }

        suggestions.remove(primaryDtc)
        return suggestions.toList().sorted()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // compareWithCurrent — Generar lista de ValueDifference
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Genera la lista de diferencias entre el freeze frame y los valores actuales.
     *
     * @param freezeFrame    Datos del freeze frame.
     * @param currentValues  Mapa PID → valor actual (de ScanUiState.readings).
     */
    fun compareWithCurrent(
        freezeFrame: FreezeFrameData,
        currentValues: Map<String, Float>
    ): List<ValueDifference> {
        val params = listOf(
            Triple("0C", "RPM",                   "rpm"),
            Triple("0D", "Velocidad",              "km/h"),
            Triple("05", "Temp refrigerante",      "°C"),
            Triple("11", "Posición mariposa",      "%"),
            Triple("04", "Carga motor",            "%"),
            Triple("06", "Trim corto plazo",       "%"),
            Triple("07", "Trim largo plazo",       "%"),
            Triple("0B", "Presión MAP",            "kPa"),
            Triple("0E", "Avance encendido",       "°CA"),
            Triple("14", "O2 B1S1",                "V"),
            Triple("15", "O2 B1S2",                "V"),
            Triple("42", "Voltaje batería",         "V")
        )
        val normalRanges = mapOf(
            "0C" to (500f to 6000f),
            "0D" to (0f to 250f),
            "05" to (75f to 105f),
            "11" to (0f to 100f),
            "04" to (0f to 80f),
            "06" to (-10f to 10f),
            "07" to (-15f to 15f),
            "0B" to (25f to 105f),
            "0E" to (0f to 45f),
            "14" to (0.1f to 0.9f),
            "15" to (0.1f to 0.9f),
            "42" to (11.5f to 14.5f)
        )

        return params.mapNotNull { (pid, name, unit) ->
            val ffVal = freezeFrame.valueOf(pid)?.toFloat() ?: return@mapNotNull null
            val curVal = currentValues[pid] ?: Float.NaN
            val (nMin, nMax) = normalRanges[pid] ?: (null to null)
            ValueDifference(
                parameterName    = name,
                pid              = pid,
                unit             = unit,
                freezeFrameValue = ffVal,
                currentValue     = curVal,
                normalMin        = nMin,
                normalMax        = nMax
            )
        }
    }
}
