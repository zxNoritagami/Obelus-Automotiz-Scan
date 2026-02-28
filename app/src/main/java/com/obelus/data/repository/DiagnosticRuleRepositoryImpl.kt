package com.obelus.data.repository

import com.obelus.data.local.dao.DiagnosticRuleDao
import com.obelus.data.local.entity.DiagnosticRule
import com.obelus.domain.repository.DiagnosticRuleRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del repositorio de reglas de diagnóstico.
 * Gestiona la persistencia y la inicialización de datos (seeding).
 */
@Singleton
class DiagnosticRuleRepositoryImpl @Inject constructor(
    private val diagnosticRuleDao: DiagnosticRuleDao
) : DiagnosticRuleRepository {

    override suspend fun getRulesByDtc(dtcCode: String): List<DiagnosticRule> {
        return diagnosticRuleDao.getRulesByDtc(dtcCode)
    }

    override suspend fun getAllRules(): List<DiagnosticRule> {
        return diagnosticRuleDao.getAllRules()
    }

    /**
     * Inserta reglas iniciales si la base de datos está vacía.
     */
    override suspend fun seedRulesIfEmpty() {
        val currentRules = diagnosticRuleDao.getAllRules()
        if (currentRules.isEmpty()) {
            val initialRules = listOf(
                DiagnosticRule(
                    dtcCode = "P0302",
                    requiredConditions = "{'RPM': {'>': 600}, 'MISFIRE_C2': {'>': 10}}",
                    weight = 0.8,
                    probableCause = "Fallo localizado en cilindro 2 (Bujía o Bobina)",
                    isRootCandidate = true,
                    severityLevel = 4
                ),
                DiagnosticRule(
                    dtcCode = "P0172",
                    requiredConditions = "{'STFT': {'<': -15}, 'LTFT': {'<': -10}}",
                    weight = 0.7,
                    probableCause = "Sistema demasiado rico (Exceso de combustible)",
                    isRootCandidate = true,
                    severityLevel = 3
                ),
                DiagnosticRule(
                    dtcCode = "P0101",
                    requiredConditions = "{'MAF': {'out_of_range': true}}",
                    weight = 0.6,
                    probableCause = "Sensor MAF sucio o defectuoso",
                    isRootCandidate = true,
                    severityLevel = 2
                ),
                DiagnosticRule(
                    dtcCode = "P0796",
                    requiredConditions = "{'TRANS_TEMP': {'>': 110}}",
                    weight = 0.9,
                    probableCause = "Solenoide de presión de transmisión C pegado",
                    isRootCandidate = true,
                    severityLevel = 5
                ),
                DiagnosticRule(
                    dtcCode = "P0087",
                    requiredConditions = "{'FUEL_PRESSURE': {'<': 2000}}",
                    weight = 0.85,
                    probableCause = "Presión de riel de combustible baja (Bomba o Filtro)",
                    isRootCandidate = true,
                    severityLevel = 4
                )
            )
            diagnosticRuleDao.insertRules(initialRules)
        }
    }
}
