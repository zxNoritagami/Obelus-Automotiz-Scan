package com.obelus.domain.model

/**
 * Representa el índice de salud general del vehículo (0-100).
 */
data class VehicleHealthScore(
    val score: Int, // 100 = Perfecto, 0 = Crítico
    val mainFactors: List<String>, // Factores que influyeron en el score (ej. "Alta temperatura", "DTCs activos")
    val summary: String
)
