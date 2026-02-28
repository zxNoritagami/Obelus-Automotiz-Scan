package com.obelus.domain.model

import java.util.Date

/**
 * Representa el resultado consolidado de una sesión de diagnóstico avanzado.
 * Fusiona reglas persistidas, anomalías estadísticas e inconsistencias de sensores
 * mediante un modelo probabilístico normalizado.
 */
data class DiagnosticReport(
    /**
     * Lista de hallazgos calculados por el motor de diagnóstico, ordenados por probabilidad.
     */
    val findings: List<DiagnosticFinding>,
    
    /**
     * Resultados brutos de detección de anomalías para referencia.
     */
    val anomalyResults: List<AnomalyDetectionResult>,
    
    /**
     * Resultados brutos de validación de consistencia para referencia.
     */
    val consistencyResults: List<SensorConsistencyResult>,

    /**
     * Suma total de las probabilidades no normalizadas (Evidencia total).
     */
    val totalProbabilityMass: Double,

    /**
     * Versión del modelo de diagnóstico utilizado.
     */
    val modelVersion: String = "Bayes-Simplified-v1",
    
    /**
     * Timestamp de generación del reporte.
     */
    val generatedAt: Long = System.currentTimeMillis(),

    /**
     * Campos heredados de la versión skeletal para compatibilidad
     */
    val id: String = "",
    val timestamp: Date = Date(),
    val vin: String = "",
    val healthScore: VehicleHealthScore = VehicleHealthScore(100, emptyList(), ""),
    val probableCauses: List<ProbableCause> = emptyList(),
    val detectedAnomalies: List<Anomaly> = emptyList(),
    val consistencyReports: List<ConsistencyReport> = emptyList()
)

/**
 * Detalle de una causa probable identificada por el motor experto.
 */
data class ProbableCause(
    val description: String,
    val probability: Float, // 0.0 to 1.0
    val severity: String,   // LOW, MEDIUM, HIGH, CRITICAL
    val isRootCause: Boolean,
    val relatedDtc: String? = null
)
