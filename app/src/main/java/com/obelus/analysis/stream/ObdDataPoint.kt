package com.obelus.analysis.stream

/**
 * Representa una lectura individual de un sensor OBD2 en un punto específico del tiempo.
 */
data class ObdDataPoint(
    val pid: String,
    val value: Double,
    val timestamp: Long = System.currentTimeMillis()
)
