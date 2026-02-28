package com.obelus.domain.model

/**
 * Resultado detallado de un análisis de detección de anomalías para un PID.
 */
data class AnomalyDetectionResult(
    val pid: String,
    val mean: Double,
    val stdDeviation: Double,
    val lastValue: Double,
    val isAnomalous: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
