package com.obelus.analysis.validation

/**
 * Define los tipos de relaciones físicas y lógicas esperadas entre dos sensores.
 */
enum class RelationshipType {
    /**
     * Ambos sensores deben aumentar o disminuir en la misma dirección.
     * Ej: RPM y Masa de Aire (MAF).
     */
    DIRECT_CORRELATION,

    /**
     * Cuando un sensor aumenta, el otro debe disminuir.
     * Ej: Vacío del múltiple vs Posición del acelerador.
     */
    INVERSE_CORRELATION,

    /**
     * La relación (división) entre ambos sensores debe mantenerse dentro de un rango.
     * Ej: Velocidad Entrada / Velocidad Salida (Slip Ratio en CVT).
     */
    RATIO_EXPECTED,

    /**
     * La diferencia absoluta entre ambos sensores no debe exceder un umbral.
     * Ej: Comparación entre dos sensores de temperatura de refrigerante.
     */
    DELTA_THRESHOLD
}
