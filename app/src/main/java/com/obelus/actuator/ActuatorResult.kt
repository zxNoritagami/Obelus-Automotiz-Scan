package com.obelus.actuator

import com.obelus.domain.model.ActuatorTest
import com.obelus.domain.model.DangerLevel

// ─────────────────────────────────────────────────────────────────────────────
// ActuatorResult.kt
// Estados de resultado de un test de actuador OBD2.
// Sealed class que reemplaza / complementa TestResult con estados específicos
// de hardware (NotSupported, Danger).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Estados posibles durante la ejecución de un test de actuador.
 */
sealed class ActuatorResult {

    /** Test en progreso — emitido periódicamente. */
    data class Running(
        val test: ActuatorTest,
        val message: String,
        val elapsedMs: Long,
        val progressFraction: Float   // 0.0–1.0
    ) : ActuatorResult()

    /** Test completado – el actuador respondió correctamente. */
    data class Success(
        val test: ActuatorTest,
        val rawResponse: String,
        val parsedValue: String,
        val elapsedMs: Long
    ) : ActuatorResult()

    /** El ECU no respondió o devolvió error. */
    data class Failed(
        val test: ActuatorTest,
        val reason: String,
        val rawResponse: String? = null
    ) : ActuatorResult()

    /** Timeout: no hubo respuesta del ECU en el tiempo máximo. */
    data class Timeout(
        val test: ActuatorTest,
        val timeoutMs: Long
    ) : ActuatorResult()

    /** El vehículo conectado no soporta este test. */
    data class NotSupported(
        val test: ActuatorTest,
        val reason: String = "El vehículo no responde a este PID"
    ) : ActuatorResult()

    /**
     * Condiciones inseguras detectadas antes de iniciar el test.
     * El test NO fue ejecutado.
     */
    data class Danger(
        val test: ActuatorTest,
        val violations: List<SafetyViolation>
    ) : ActuatorResult()
}

/** Condición de seguridad no satisfecha. */
data class SafetyViolation(
    val condition: String,
    val measured: String,
    val required: String
)
