package com.obelus.protocol

// ─────────────────────────────────────────────────────────────────────────────
// OBD2Protocol.kt
// Enum con todos los protocolos OBD-II soportados por el ELM327.
// Pure Kotlin – sin dependencias de Android.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Representa cada protocolo de bus de vehículo que el ELM327 puede manejar.
 *
 * @param code       Código numérico del protocolo (usado en "AT SP X").
 * @param atCommand  Comando AT completo para seleccionar este protocolo, ej. "AT SP 0".
 * @param protocolName Nombre corto del protocolo.
 * @param description  Descripción técnica extendida.
 */
enum class OBD2Protocol(
    val code: Int,
    val atCommand: String,
    val protocolName: String,
    val description: String
) {
    /** Detección automática del protocolo por el ELM327. */
    AUTO(
        code       = 0,
        atCommand  = "AT SP 0",
        protocolName = "Auto",
        description = "Detección automática de protocolo (ELM327)"
    ),

    /** SAE J1850 PWM – 41.6 kbaud. Usado en vehículos Ford más antiguos. */
    J1850_PWM(
        code       = 1,
        atCommand  = "AT SP 1",
        protocolName = "SAE J1850 PWM",
        description = "41.6 kbaud – Ford legacy"
    ),

    /** SAE J1850 VPW – 10.4 kbaud. Usado en GM más antiguos. */
    J1850_VPW(
        code       = 2,
        atCommand  = "AT SP 2",
        protocolName = "SAE J1850 VPW",
        description = "10.4 kbaud – GM legacy"
    ),

    /** ISO 9141-2 / K-LINE – 5 baud init, 10.4 kbaud. */
    KLINE_ISO9141(
        code       = 3,
        atCommand  = "AT SP 3",
        protocolName = "ISO 9141-2 K-LINE",
        description = "5 baud init, 10.4 kbaud – Europeo/Asiático antiguo"
    ),

    /** ISO 14230-4 KWP2000 – 5 baud init, 10.4 kbaud. */
    KLINE_ISO14230(
        code       = 4,
        atCommand  = "AT SP 4",
        protocolName = "ISO 14230-4 KWP",
        description = "5 baud init, 10.4 kbaud – KWP2000"
    ),

    /** ISO 14230-4 KWP2000 – Fast init, 10.4 kbaud. */
    KWP2000_FAST(
        code       = 5,
        atCommand  = "AT SP 5",
        protocolName = "ISO 14230-4 KWP Fast",
        description = "Fast init, 10.4 kbaud – KWP2000"
    ),

    /** ISO 15765-4 CAN – 11-bit ID, 500 kbaud. Protocolo más común en vehículos modernos. */
    CAN_11BIT_500K(
        code       = 6,
        atCommand  = "AT SP 6",
        protocolName = "ISO 15765-4 CAN 11/500",
        description = "11-bit ID, 500 kbaud – CAN estándar moderno"
    ),

    /** ISO 15765-4 CAN – 29-bit ID, 500 kbaud. */
    CAN_29BIT_500K(
        code       = 7,
        atCommand  = "AT SP 7",
        protocolName = "ISO 15765-4 CAN 29/500",
        description = "29-bit ID, 500 kbaud – CAN extendido"
    ),

    /** ISO 15765-4 CAN – 11-bit ID, 250 kbaud. */
    CAN_11BIT_250K(
        code       = 8,
        atCommand  = "AT SP 8",
        protocolName = "ISO 15765-4 CAN 11/250",
        description = "11-bit ID, 250 kbaud – CAN estándar lento"
    ),

    /** ISO 15765-4 CAN – 29-bit ID, 250 kbaud. */
    CAN_29BIT_250K(
        code       = 9,
        atCommand  = "AT SP 9",
        protocolName = "ISO 15765-4 CAN 29/250",
        description = "29-bit ID, 250 kbaud – CAN extendido lento"
    ),

    /** SAE J1939 CAN – 29-bit ID, 250 kbaud. Usado en camiones. */
    SAE_J1939(
        code       = 10,
        atCommand  = "AT SP A",
        protocolName = "SAE J1939 CAN",
        description = "29-bit ID, 250 kbaud – Trucks / Heavy duty"
    ),

    /** CAN definido por usuario 1. */
    USER1_CAN(
        code       = 11,
        atCommand  = "AT SP B",
        protocolName = "User1 CAN",
        description = "11-bit ID, 125 kbaud (configurable)"
    ),

    /** CAN definido por usuario 2. */
    USER2_CAN(
        code       = 12,
        atCommand  = "AT SP C",
        protocolName = "User2 CAN",
        description = "11-bit ID, 50 kbaud (configurable)"
    );

    /** Alias de retrocompatibilidad con código anterior que usaba [id]. */
    val id: Int get() = code

    companion object {
        /**
         * Devuelve el protocolo para el código dado, o [AUTO] si no se reconoce.
         */
        fun fromId(id: Int): OBD2Protocol =
            entries.find { it.code == id } ?: AUTO

        /**
         * Parsea la respuesta del comando "AT DP" devuelta por el ELM327 e intenta
         * mapearla a un [OBD2Protocol] conocido.
         *
         * La respuesta suele ser un texto como "ISO 15765-4 (CAN 11/500)" o
         * simplemente un número como "6".
         */
        fun parseFromDpResponse(response: String): OBD2Protocol {
            val clean = response.trim().uppercase()

            // Intentar por número primero (respuesta de AT DPN)
            clean.toIntOrNull()?.let { return fromId(it) }

            return when {
                clean.contains("AUTO")             -> AUTO
                clean.contains("J1850") && clean.contains("PWM") -> J1850_PWM
                clean.contains("J1850") && clean.contains("VPW") -> J1850_VPW
                clean.contains("9141")             -> KLINE_ISO9141
                clean.contains("14230") && (clean.contains("FAST") || clean.contains("QUICK")) -> KWP2000_FAST
                clean.contains("14230")            -> KLINE_ISO14230
                clean.contains("CAN") && clean.contains("11") && clean.contains("500") -> CAN_11BIT_500K
                clean.contains("CAN") && clean.contains("29") && clean.contains("500") -> CAN_29BIT_500K
                clean.contains("CAN") && clean.contains("11") && clean.contains("250") -> CAN_11BIT_250K
                clean.contains("CAN") && clean.contains("29") && clean.contains("250") -> CAN_29BIT_250K
                clean.contains("J1939")            -> SAE_J1939
                else -> AUTO
            }
        }
    }
}
