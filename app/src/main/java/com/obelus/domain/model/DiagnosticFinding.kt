package com.obelus.domain.model

/**
 * Representa un hallazgo de diagnóstico individual con su probabilidad calculada.
 * Utiliza una aproximación bayesiana simplificada: Posterior ∝ Prior × Likelihood.
 */
data class DiagnosticFinding(
    val dtcCode: String,
    val probableCause: String,
    
    /**
     * Probabilidad a priori basada en el peso histórico de la regla (baseWeight).
     */
    val priorProbability: Double,
    
    /**
     * Factor de verosimilitud (Likelihood) basado en la evidencia actual (anomalías e inconsistencias).
     */
    val likelihood: Double,
    
    /**
     * Probabilidad a posteriori después de la normalización.
     */
    val posteriorProbability: Double,
    
    /**
     * Nivel de severidad de la regla (1-5).
     */
    val severityLevel: Int,

    /**
     * Indica si la regla es una candidata a ser la causa raíz del problema.
     */
    val isRootCandidate: Boolean
)
