package com.obelus.protocol

// ─────────────────────────────────────────────────────────────────────────────
// ATCommand.kt
// Constantes de los comandos AT del ELM327.
// Pure Kotlin – sin dependencias de Android.
// ─────────────────────────────────────────────────────────────────────────────

object ATCommand {

    // ── Reset / Inicialización ───────────────────────────────────────────────
    /** Restablece el ELM327 a sus valores de fábrica. */
    const val RESET              = "ATZ"

    /** Desactiva el eco de comandos (recomendado para parseo). */
    const val ECHO_OFF           = "ATE0"

    /** Activa el eco de comandos. */
    const val ECHO_ON            = "ATE1"

    /** Desactiva saltos de línea en las respuestas. */
    const val LINE_FEED_OFF      = "ATL0"

    /** Activa saltos de línea en las respuestas. */
    const val LINE_FEED_ON       = "ATL1"

    /** Incluye los CAN headers en las respuestas. */
    const val HEADERS_ON         = "ATH1"

    /** Suprime los CAN headers en las respuestas. */
    const val HEADERS_OFF        = "ATH0"

    /** Elimina los espacios de las respuestas (más veloz para parseo). */
    const val SPACES_OFF         = "ATS0"

    /** Incluye espacios en las respuestas (más legible). */
    const val SPACES_ON          = "ATS1"

    // ── Versión ─────────────────────────────────────────────────────────────
    /** Imprime la identificación del chip ELM327 y su versión de firmware. */
    const val PRINT_VERSION      = "ATI"

    // ── Protocolo ───────────────────────────────────────────────────────────
    /**
     * Prefijo para seleccionar protocolo. Concatenar con el código del protocolo.
     * Ej.: "$SET_PROTOCOL${OBD2Protocol.CAN_11BIT_500K.code}" → "ATSP6"
     */
    const val SET_PROTOCOL       = "AT SP "   // + código numérico (0–C)

    /** Describe el protocolo actualmente activo (texto largo). */
    const val DESCRIBE_PROTOCOL  = "AT DP"

    /** Describe el número del protocolo actualmente activo. */
    const val DESCRIBE_PROTOCOL_NUM = "AT DPN"

    // ── CAN / ISO-TP ─────────────────────────────────────────────────────────
    /**
     * Activa el formato automático CAN (ISO-TP / ISO 15765-4).
     * El chip gestiona los frames y el usuario ve sólo la carga útil.
     */
    const val CAN_AUTO_FORMAT    = "ATCAF1"

    /** Desactiva el formato automático CAN (raw frames). */
    const val CAN_AUTO_FORMAT_OFF = "ATCAF0"

    /** Permite mensajes largos (>7 bytes por trama). */
    const val ALLOW_LONG_MESSAGES = "ATAL"

    /** Timing adaptativo – modo 1 (recomendado para la mayoría de los vehículos). */
    const val ADAPTIVE_TIMING_AUTO_1 = "ATAT1"

    // ── Filtros y headers ────────────────────────────────────────────────────
    /** Prefijo para fijar el header CAN de TX. Concatenar con el ID. Ej.: "AT SH 7E0". */
    const val SET_HEADER         = "AT SH "

    /** Prefijo para fijar el filtro de respuesta CAN. Ej.: "AT CRA 7E8". */
    const val SET_RECEIVE_FILTER = "AT CRA "

    /** Prefijo para fijar el timeout (1 count = 4 ms). Ej.: "AT ST 19" → 100 ms. */
    const val SET_TIMEOUT        = "AT ST "

    // ── Diagnóstico ──────────────────────────────────────────────────────────
    /** Lee el voltaje de la batería del vehículo. */
    const val READ_VOLTAGE       = "AT RV"
}
