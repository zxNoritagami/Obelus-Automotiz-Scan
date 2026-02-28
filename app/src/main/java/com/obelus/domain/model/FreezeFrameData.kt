package com.obelus.domain.model

/**
 * Representa los datos del cuadro congelado (Freeze Frame) capturados por la ECU
 * en el momento en que se registró un código de falla (DTC).
 * Modo 02 de OBD2.
 */
data class FreezeFrameData(
    val dtcCode: String,
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * Mapa de PIDs y sus valores en el momento de la falla.
     * Ejemplo: "0C" (RPM) -> 2500.0
     */
    val sensorValues: Map<String, Double>
)
