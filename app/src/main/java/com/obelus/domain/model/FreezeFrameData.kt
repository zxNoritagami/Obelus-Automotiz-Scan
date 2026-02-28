package com.obelus.domain.model

// ─────────────────────────────────────────────────────────────────────────────
// FreezeFrameData.kt  —  Modo 02 OBD2 Freeze Frame (snapshot completo)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Snapshot completo de parámetros del motor capturado por la ECU
 * en el instante exacto en que se generó un DTC.
 *
 * @param dtcCode                   DTC que disparó el freeze frame (ej. "P0420").
 * @param timestamp                 Epoch ms del momento de captura.
 * @param sessionId                 ID de sesión Room asociada (puede ser "live" si no hay sesión guardada).
 *
 * Parámetros OBD2 Modo 02 / 01:
 * @param rpm                       Revoluciones del motor (rpm).
 * @param vehicleSpeed              Velocidad del vehículo (km/h).
 * @param coolantTemp               Temperatura del refrigerante (°C).
 * @param throttlePosition          Posición de la mariposa (%).
 * @param engineLoad                Carga calculada del motor (%).
 * @param fuelTrimShort             Corrección de mezcla a corto plazo banco 1 (%).
 * @param fuelTrimLong              Corrección de mezcla a largo plazo banco 1 (%).
 * @param intakePressure            Presión del colector de admisión (kPa).
 * @param timingAdvance             Avance de encendido (°CA BTDC).
 * @param o2SensorBank1             Tensión sensor O2 banco 1, sensor 1 (V).
 * @param o2SensorBank2             Tensión sensor O2 banco 1, sensor 2 (V).
 * @param batteryVoltage            Voltaje del sistema eléctrico (V).
 * @param distanceSinceDtcCleared   Distancia recorrida desde el último borrado de DTCs (km).
 * @param rawSensorValues           Mapa completo PID→valor para PIDs no tipados (retrocompat).
 */
data class FreezeFrameData(
    val dtcCode: String,
    val timestamp: Long                    = System.currentTimeMillis(),
    val sessionId: String                  = "live",

    // ── Parámetros tipados ──────────────────────────────────────────────────
    val rpm: Float?                        = null,
    val vehicleSpeed: Float?               = null,
    val coolantTemp: Float?                = null,
    val throttlePosition: Float?           = null,
    val engineLoad: Float?                 = null,
    val fuelTrimShort: Float?              = null,
    val fuelTrimLong: Float?               = null,
    val intakePressure: Float?             = null,
    val timingAdvance: Float?              = null,
    val o2SensorBank1: Float?              = null,
    val o2SensorBank2: Float?              = null,
    val batteryVoltage: Float?             = null,
    val distanceSinceDtcCleared: Int?      = null,

    /** Mapa PID → valor para datos no en los campos tipados (retrocompatibilidad). */
    val rawSensorValues: Map<String, Double> = emptyMap()
) {
    // ── Derived ───────────────────────────────────────────────────────────────

    /** Todos los parámetros tipados no-null como lista de pares (nombre, valor). */
    fun toNamedList(): List<Pair<String, Float>> = buildList {
        rpm?. let              { add("RPM"                  to it) }
        vehicleSpeed?.let      { add("Velocidad km/h"       to it) }
        coolantTemp?.let       { add("Temp refrigerante °C" to it) }
        throttlePosition?.let  { add("Posición mariposa %"  to it) }
        engineLoad?.let        { add("Carga motor %"        to it) }
        fuelTrimShort?.let     { add("Trim corto plazo %"   to it) }
        fuelTrimLong?.let      { add("Trim largo plazo %"   to it) }
        intakePressure?.let    { add("Presión MAP kPa"      to it) }
        timingAdvance?.let     { add("Avance encendido °"   to it) }
        o2SensorBank1?.let     { add("O2 B1S1 V"           to it) }
        o2SensorBank2?.let     { add("O2 B1S2 V"           to it) }
        batteryVoltage?.let    { add("Voltaje batería V"    to it) }
        distanceSinceDtcCleared?.let { add("Dist. desde borrado km" to it.toFloat()) }
    }

    /** Resumen compacto de condición (rpm + velocidad + carga). */
    fun conditionSummary(): String {
        val parts = buildList {
            rpm?.let          { add("${"%.0f".format(it)} rpm") }
            vehicleSpeed?.let { add("${"%.0f".format(it)} km/h") }
            engineLoad?.let   { add("carga ${"%.0f".format(it)}%") }
        }
        return if (parts.isEmpty()) "Sin datos" else parts.joinToString(" · ")
    }

    /** Retorna el valor de un PID genérico consultando primero los campos tipados. */
    fun valueOf(pid: String): Double? = when (pid.uppercase()) {
        "0C" -> rpm?.toDouble()
        "0D" -> vehicleSpeed?.toDouble()
        "05" -> coolantTemp?.toDouble()
        "11" -> throttlePosition?.toDouble()
        "04" -> engineLoad?.toDouble()
        "06" -> fuelTrimShort?.toDouble()
        "07" -> fuelTrimLong?.toDouble()
        "0B" -> intakePressure?.toDouble()
        "0E" -> timingAdvance?.toDouble()
        "14" -> o2SensorBank1?.toDouble()
        "15" -> o2SensorBank2?.toDouble()
        "42" -> batteryVoltage?.toDouble()
        else -> rawSensorValues[pid]
    }
}

/** Resultado devuelto por FreezeFrameRepository al leer del ECU. */
sealed class FreezeFrameResult {
    data class Success(val data: FreezeFrameData) : FreezeFrameResult()
    data class NotAvailable(val dtcCode: String, val reason: String = "No hay freeze frame para este DTC") : FreezeFrameResult()
    data class ReadError(val dtcCode: String, val message: String) : FreezeFrameResult()
}
