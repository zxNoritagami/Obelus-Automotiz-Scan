package com.obelus.diagnostic.engine

import com.obelus.analysis.stream.DataStreamAnalyzer
import com.obelus.analysis.validation.SensorConsistencyValidator
import com.obelus.domain.model.*
import com.obelus.domain.repository.DiagnosticRuleRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de diagnóstico experto con inferencia bayesiana y dependencias condicionales.
 * Permite modelar relaciones causa-efecto entre diferentes fallos del vehículo.
 */
@Singleton
class DiagnosticEngine @Inject constructor(
    private val ruleRepository: DiagnosticRuleRepository,
    private val streamAnalyzer: DataStreamAnalyzer,
    private val consistencyValidator: SensorConsistencyValidator
) {

    /**
     * Lista de dependencias técnicas predefinidas.
     * Modela cómo un fallo primario aumenta o disminuye la probabilidad de fallos derivados.
     */
    private val ruleDependencies = listOf(
        RuleDependency(
            parentDtc = "P0172", // Mezcla Rica
            childDtc = "P0420",  // Eficiencia Catalizador
            influenceFactor = 1.4,
            description = "Mezcla rica prolongada daña el catalizador por sobrecalentamiento."
        ),
        RuleDependency(
            parentDtc = "P0302", // Misfire C2
            childDtc = "P0300",  // Misfire Aleatorio
            influenceFactor = 1.3,
            description = "Fallo en un cilindro puede inducir fallos de encendido generales."
        ),
        RuleDependency(
            parentDtc = "P0796", // Slip Transmisión
            childDtc = "P0218",  // Sobretemperatura Transmisión
            influenceFactor = 1.5,
            description = "Deslizamiento en CVT genera calor excesivo rápidamente."
        )
    )

    /**
     * Realiza un análisis profundo con ajuste de dependencias condicionales.
     */
    suspend fun analyze(activeDtcs: List<String>): DiagnosticReport {
        // Paso 1: Cálculo inicial de probabilidades (sin dependencias)
        val initialFindings = calculateProbabilities(activeDtcs, emptyList())
        
        // Paso 2: Identificar "Padres Confirmados" (Probabilidad > 60%)
        val confirmedParents = initialFindings.filter { it.posteriorProbability > 0.6 }
            .map { it.dtcCode }
        
        // Paso 3: Re-calcular con ajuste de Priors basado en dependencias
        val finalFindings = calculateProbabilities(activeDtcs, confirmedParents)

        return DiagnosticReport(
            findings = finalFindings,
            anomalyResults = activeDtcs.mapNotNull { streamAnalyzer.analyzeAnomaly(it) },
            consistencyResults = consistencyValidator.validateAll(),
            totalProbabilityMass = finalFindings.sumOf { it.priorProbability * it.likelihood }
        )
    }

    /**
     * Núcleo del cálculo bayesiano.
     * @param activeDtcs Códigos detectados.
     * @param confirmedParentDtcs Códigos que actúan como disparadores de dependencias.
     */
    private suspend fun calculateProbabilities(
        activeDtcs: List<String>,
        confirmedParentDtcs: List<String>
    ): List<DiagnosticFinding> {
        val drafts = mutableListOf<DiagnosticFindingDraft>()
        val consistencyResults = consistencyValidator.validateAll()

        activeDtcs.forEach { dtc ->
            val rules = ruleRepository.getRulesByDtc(dtc)
            rules.forEach { rule ->
                // Ajuste de Prior por Dependencia
                var adjustedPrior = rule.weight
                
                ruleDependencies.forEach { dep ->
                    if (dep.childDtc == rule.dtcCode && confirmedParentDtcs.contains(dep.parentDtc)) {
                        adjustedPrior *= dep.influenceFactor
                    }
                }
                adjustedPrior = adjustedPrior.coerceIn(0.0, 1.0)

                // Cálculo de Likelihood (Evidencia en tiempo real)
                var likelihood = 1.0
                val anomaly = streamAnalyzer.analyzeAnomaly(dtc)
                if (anomaly?.isAnomalous == true) likelihood += 0.25
                
                val relevantInconsistencies = consistencyResults.count { !it.isConsistent }
                likelihood -= (relevantInconsistencies * 0.10)
                val finalLikelihood = likelihood.coerceAtLeast(0.1)

                val unnormalizedPosterior = adjustedPrior * finalLikelihood

                drafts.add(
                    DiagnosticFindingDraft(
                        dtcCode = rule.dtcCode,
                        probableCause = rule.probableCause,
                        prior = adjustedPrior,
                        likelihood = finalLikelihood,
                        unnormalizedPosterior = unnormalizedPosterior,
                        severityLevel = rule.severityLevel,
                        isRootCandidate = rule.isRootCandidate
                    )
                )
            }
        }

        // Normalización Global
        val totalMass = drafts.sumOf { it.unnormalizedPosterior }
        return drafts.map { d ->
            val posterior = if (totalMass > 0) d.unnormalizedPosterior / totalMass else 1.0 / drafts.size.coerceAtLeast(1)
            DiagnosticFinding(
                dtcCode = d.dtcCode,
                probableCause = d.probableCause,
                priorProbability = d.prior,
                likelihood = d.likelihood,
                posteriorProbability = posterior,
                severityLevel = d.severityLevel,
                isRootCandidate = d.isRootCandidate
            )
        }.sortedByDescending { it.posteriorProbability }
    }

    private data class DiagnosticFindingDraft(
        val dtcCode: String,
        val probableCause: String,
        val prior: Double,
        val likelihood: Double,
        val unnormalizedPosterior: Double,
        val severityLevel: Int,
        val isRootCandidate: Boolean
    )
}
