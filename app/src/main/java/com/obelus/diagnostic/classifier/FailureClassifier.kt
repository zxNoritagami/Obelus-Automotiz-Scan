package com.obelus.diagnostic.classifier

import com.obelus.domain.model.ProbableCause
import javax.inject.Inject

/**
 * Encargado de clasificar los fallos detectados en:
 * ROOT_CAUSE (Causa raíz) vs SECONDARY_EFFECT (Efectos secundarios).
 */
class FailureClassifier @Inject constructor() {

    /**
     * Analiza una lista de causas probables y las clasifica según su jerarquía técnica.
     * @param causes Lista de causas detectadas por el motor.
     * @return Lista de causas con su flag isRootCause actualizado.
     */
    fun classifyFailures(causes: List<com.obelus.domain.model.ProbableCause>): List<com.obelus.domain.model.ProbableCause> {
        // Reservado para Fase 4: Lógica de clasificación por prioridad y relación
        return causes
    }
}
