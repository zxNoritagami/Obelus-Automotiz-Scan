package com.obelus.domain.model

/**
 * Define una relación de dependencia entre dos reglas de diagnóstico.
 * Permite modelar cómo la presencia de una falla (parent) influye en la probabilidad de otra (child).
 */
data class RuleDependency(
    val parentDtc: String,
    val childDtc: String,
    
    /**
     * Factor que multiplica el peso base (prior) de la regla hija si la regla padre 
     * tiene una alta probabilidad confirmada.
     * Rango recomendado: 0.5 (inhibición) a 1.5 (excitación).
     */
    val influenceFactor: Double,
    
    val description: String
)
