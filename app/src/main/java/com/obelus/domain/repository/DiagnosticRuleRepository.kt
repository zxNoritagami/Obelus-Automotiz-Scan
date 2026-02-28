package com.obelus.domain.repository

import com.obelus.data.local.entity.DiagnosticRule

/**
 * Interfaz del repositorio para la gestión de reglas de diagnóstico.
 */
interface DiagnosticRuleRepository {
    suspend fun getRulesByDtc(dtcCode: String): List<DiagnosticRule>
    suspend fun getAllRules(): List<DiagnosticRule>
    suspend fun seedRulesIfEmpty()
}
