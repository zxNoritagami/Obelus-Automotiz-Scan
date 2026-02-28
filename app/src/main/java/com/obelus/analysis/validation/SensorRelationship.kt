package com.obelus.analysis.validation

/**
 * Define una relación técnica entre dos sensores (PIDs) para validar su coherencia.
 */
data class SensorRelationship(
    val primaryPid: String,
    val secondaryPid: String,
    val relationshipType: RelationshipType,
    val tolerance: Double,
    val description: String
)
