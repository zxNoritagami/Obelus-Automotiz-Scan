package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad que define una regla de diagnóstico para el motor experto.
 * Permite evaluar condiciones sobre datos OBD2 y DTCs para inferir causas raíz.
 */
@Entity(tableName = "diagnostic_rules")
data class DiagnosticRule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    /**
     * Código DTC al que aplica esta regla (ej. "P0302"). 
     */
    val dtcCode: String,
    
    /**
     * Condiciones requeridas en formato procesable (JSON o string estructurado).
     * Ej: "{'RPM': {'>': 3000}, 'STFT': {'>': 15}}"
     */
    val requiredConditions: String,
    
    /**
     * Peso o importancia de esta regla en el cálculo de probabilidad (0.0 a 1.0).
     */
    val weight: Double,
    
    /**
     * Descripción técnica de la causa probable.
     */
    val probableCause: String,
    
    /**
     * Indica si esta regla es una candidata a ser la causa raíz (ROOT_CAUSE).
     */
    val isRootCandidate: Boolean,

    /**
     * Nivel de severidad del problema (1 a 5).
     */
    val severityLevel: Int,

    /**
     * Timestamp de creación de la regla.
     */
    val createdAt: Long = System.currentTimeMillis()
)
