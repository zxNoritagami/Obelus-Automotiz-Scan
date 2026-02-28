package com.obelus.manufacturer

// ─────────────────────────────────────────────────────────────────────────────
// ResponseType.kt
// Estrategia de decodificación de la respuesta cruda del ECU.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tipo de interpretación de la respuesta hexadecimal del ECU.
 *
 * Cada valor determina cómo [ManufacturerDataRepository.decodeResponse] convierte
 * los bytes recibidos en un Float (o índice de tabla).
 */
enum class ResponseType(val description: String) {

    /** Un único byte (0–255). Fórmula: value × scale + offset. */
    SINGLE_BYTE("Un byte directo"),

    /** Dos bytes en orden big-endian (byte alto primero). Rango 0–65535. */
    TWO_BYTES_BIG_ENDIAN("2 bytes big-endian"),

    /** Dos bytes en orden little-endian (byte bajo primero). Rango 0–65535. */
    TWO_BYTES_LITTLE_ENDIAN("2 bytes little-endian"),

    /** Cuatro bytes big-endian (odómetro, horas de motor). Rango 0–4294967295. */
    FOUR_BYTES("4 bytes big-endian"),

    /**
     * Campo de bits — cada bit es un flag independiente.
     * El Float retornado es el valor entero crudo; la UI muestra los bits individuales.
     */
    BITFIELD("Campo de bits"),

    /**
     * Codificación específica del fabricante — requiere tabla de conversión.
     * El Float retornado es el índice de tabla; la UI muestra la descripción del índice.
     */
    ENCODED("Tabla de conversión")
}
