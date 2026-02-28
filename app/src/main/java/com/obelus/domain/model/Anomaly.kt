package com.obelus.domain.model

/**
 * Representa una desviación detectada en los datos en vivo de un sensor.
 */
data class Anomaly(
    val pid: String,
    val description: String,
    val observedValue: Double,
    val expectedRange: ClosedRange<Double>,
    val severity: Int // 0 (Baja) a 100 (Crítica)
)
