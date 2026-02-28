package com.obelus.domain.usecase

import com.obelus.data.local.entity.DiagnosticRule
import com.obelus.domain.repository.DiagnosticRuleRepository
import javax.inject.Inject

/**
 * UseCase para obtener las reglas de diagnóstico asociadas a un código DTC específico.
 */
class GetDiagnosticRulesByDtcUseCase @Inject constructor(
    private val repository: DiagnosticRuleRepository
) {
    /**
     * Ejecuta la consulta de reglas.
     * @param dtcCode Código de error (ej: "P0302").
     */
    suspend operator fun invoke(dtcCode: String): List<DiagnosticRule> {
        return repository.getRulesByDtc(dtcCode)
    }
}
