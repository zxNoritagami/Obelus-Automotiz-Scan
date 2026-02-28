package com.obelus.domain.model

import com.obelus.analysis.validation.RelationshipType

/**
 * Representa el resultado de una validación de coherencia entre dos sensores.
 */
data class SensorConsistencyResult(
    val primaryPid: String,
    val secondaryPid: String,
    val relationshipType: RelationshipType,
    val isConsistent: Boolean,
    val deviation: Double,
    val timestamp: Long = System.currentTimeMillis()
)
