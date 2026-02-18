package com.obelus.data.obd2

/**
 * Represents a decoded reading from an OBD2 response.
 * Used as a DTO before persistence or UI display.
 */
data class ObdReading(
    val pid: String,
    val name: String,
    val value: Float,
    val unit: String,
    val rawData: String,      // Datos hex originales
    val timestamp: Long = System.currentTimeMillis(),
    val isValid: Boolean = true  // False si hubo error de decodificación
)
