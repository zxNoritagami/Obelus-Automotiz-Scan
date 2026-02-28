package com.obelus.domain.model

/**
 * Resultado de una validación cruzada entre dos o más sensores.
 */
data class ConsistencyReport(
    val title: String,
    val description: String,
    val severity: Severity,
    val sensorsInvolved: List<String>,
    val deviationPercentage: Float
)
