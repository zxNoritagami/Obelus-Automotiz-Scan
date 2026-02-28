package com.obelus.analysis.validation

import com.obelus.analysis.stream.DataStreamAnalyzer
import com.obelus.domain.model.SensorConsistencyResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sign

/**
 * Módulo encargado de realizar validaciones cruzadas entre múltiples sensores.
 * Detecta inconsistencias lógicas entre lecturas que deberían estar relacionadas físicamente.
 */
@Singleton
class SensorConsistencyValidator @Inject constructor(
    private val dataStreamAnalyzer: DataStreamAnalyzer
) {

    // Lista de relaciones predefinidas para validación técnica.
    // En una fase futura, esto podría ser cargado dinámicamente.
    private val standardRelationships = listOf(
        SensorRelationship(
            primaryPid = "RPM",
            secondaryPid = "MAF",
            relationshipType = RelationshipType.DIRECT_CORRELATION,
            tolerance = 0.0,
            description = "Flujo de aire debe aumentar con las RPM"
        ),
        SensorRelationship(
            primaryPid = "TRANS_INPUT_SPEED",
            secondaryPid = "TRANS_OUTPUT_SPEED",
            relationshipType = RelationshipType.RATIO_EXPECTED,
            tolerance = 0.1, // 10% de margen de deslizamiento aceptable
            description = "Relación de transmisión (Slip Ratio)"
        ),
        SensorRelationship(
            primaryPid = "TPS",
            secondaryPid = "MAP",
            relationshipType = RelationshipType.DIRECT_CORRELATION,
            tolerance = 0.0,
            description = "Presión de admisión debe correlacionarse con posición de acelerador"
        )
    )

    /**
     * Valida una relación específica basada en los datos actuales del buffer.
     * @return El resultado de la validación o null si no hay datos suficientes.
     */
    fun validateRelationship(relationship: SensorRelationship): SensorConsistencyResult? {
        val deltaPrimary = dataStreamAnalyzer.calculateDelta(relationship.primaryPid) ?: return null
        val deltaSecondary = dataStreamAnalyzer.calculateDelta(relationship.secondaryPid) ?: return null
        val meanPrimary = dataStreamAnalyzer.calculateMean(relationship.primaryPid) ?: return null
        val meanSecondary = dataStreamAnalyzer.calculateMean(relationship.secondaryPid) ?: return null

        return when (relationship.relationshipType) {
            RelationshipType.DIRECT_CORRELATION -> {
                // Si ambos deltas tienen el mismo signo (o son 0), son consistentes.
                val isConsistent = sign(deltaPrimary) == sign(deltaSecondary) || deltaPrimary == 0.0 || deltaSecondary == 0.0
                SensorConsistencyResult(
                    primaryPid = relationship.primaryPid,
                    secondaryPid = relationship.secondaryPid,
                    relationshipType = relationship.relationshipType,
                    isConsistent = isConsistent,
                    deviation = abs(deltaPrimary - deltaSecondary)
                )
            }
            RelationshipType.INVERSE_CORRELATION -> {
                // Signos opuestos = consistente.
                val isConsistent = sign(deltaPrimary) != sign(deltaSecondary)
                SensorConsistencyResult(
                    primaryPid = relationship.primaryPid,
                    secondaryPid = relationship.secondaryPid,
                    relationshipType = relationship.relationshipType,
                    isConsistent = isConsistent,
                    deviation = abs(deltaPrimary + deltaSecondary)
                )
            }
            RelationshipType.RATIO_EXPECTED -> {
                if (meanSecondary == 0.0) return null
                val ratio = meanPrimary / meanSecondary
                // Comparación con la tolerancia esperada (ej. relación de cambio fija)
                val isConsistent = abs(ratio - relationship.tolerance) <= relationship.tolerance
                SensorConsistencyResult(
                    primaryPid = relationship.primaryPid,
                    secondaryPid = relationship.secondaryPid,
                    relationshipType = relationship.relationshipType,
                    isConsistent = isConsistent,
                    deviation = abs(ratio - relationship.tolerance)
                )
            }
            RelationshipType.DELTA_THRESHOLD -> {
                val diff = abs(meanPrimary - meanSecondary)
                val isConsistent = diff <= relationship.tolerance
                SensorConsistencyResult(
                    primaryPid = relationship.primaryPid,
                    secondaryPid = relationship.secondaryPid,
                    relationshipType = relationship.relationshipType,
                    isConsistent = isConsistent,
                    deviation = diff
                )
            }
        }
    }

    /**
     * Ejecuta todas las validaciones de consistencia configuradas.
     */
    fun validateAll(): List<SensorConsistencyResult> {
        return standardRelationships.mapNotNull { validateRelationship(it) }
    }
}
