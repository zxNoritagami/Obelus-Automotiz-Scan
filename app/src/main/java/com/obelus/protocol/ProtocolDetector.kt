package com.obelus.protocol

import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// ProtocolDetector.kt
// Detecta y negocia el protocolo OBD-II de forma automática y manual.
// Pure Kotlin – sin dependencias de Android.
// ─────────────────────────────────────────────────────────────────────────────

sealed class DetectionResult {
    data class Success(val protocol: OBD2Protocol) : DetectionResult()
    data class Failure(val reason: String) : DetectionResult()
}

/**
 * Detecta el protocolo OBD-II activo en el vehículo usando un [ElmConnection].
 *
 * Estrategia:
 *  1. Inicializa el ELM327 (ATZ, ATE0, ATS0, ATH1, ATL1).
 *  2. Intenta la auto-detección nativa del chip (AT SP 0 + PID 0100).
 *  3. Si falla, prueba manualmente cada protocolo en orden de probabilidad.
 *
 * @param connection Implementación activa de [ElmConnection] (Bluetooth, WiFi, USB).
 */
class ProtocolDetector(private val connection: ElmConnection) {

    private var currentProtocol: OBD2Protocol? = null

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Intenta detectar el protocolo OBD2 de forma robusta.
     *
     * Primero realiza el flujo de auto-detección del ELM327; si no tiene éxito
     * ejecuta un escaneo manual en orden de probabilidad descendiente.
     *
     * @return [DetectionResult.Success] con el protocolo detectado, o
     *         [DetectionResult.Failure] con la razón del fallo.
     */
    suspend fun autoDetect(): DetectionResult {
        println("[ProtocolDetector] ─────────────────────────────────────────")
        println("[ProtocolDetector] Iniciando detección robusta de protocolo…")

        // Inicialización ELM327
        initializeElm()

        // ── Paso 1: Auto-detección nativa ─────────────────────────────────
        println("[ProtocolDetector] → AT SP 0 (auto-detección nativa)…")
        connection.send(ATCommand.SET_PROTOCOL + OBD2Protocol.AUTO.code)
        delay(400)

        if (testConnection()) {
            val dpnResponse = connection.send(ATCommand.DESCRIBE_PROTOCOL_NUM)
            println("[ProtocolDetector] AT DPN → \"$dpnResponse\"")
            val detected = parseProtocol(dpnResponse)
            currentProtocol = detected
            println("[ProtocolDetector] ✓ Auto-detección exitosa: ${detected.protocolName} (código ${detected.code})")
            return DetectionResult.Success(detected)
        }

        println("[ProtocolDetector] Auto-detección falló. Iniciando escaneo manual…")

        // ── Paso 2: Escaneo manual – orden de probabilidad ────────────────
        val protocolsToTry = listOf(
            OBD2Protocol.CAN_11BIT_500K,   // más común en vehículos post-2008
            OBD2Protocol.CAN_11BIT_250K,
            OBD2Protocol.CAN_29BIT_500K,
            OBD2Protocol.CAN_29BIT_250K,
            OBD2Protocol.KWP2000_FAST,
            OBD2Protocol.KLINE_ISO14230,
            OBD2Protocol.KLINE_ISO9141,
            OBD2Protocol.J1850_PWM,
            OBD2Protocol.J1850_VPW
        )

        for (protocol in protocolsToTry) {
            delay(300) // respeto entre intentos
            if (tryProtocol(protocol)) {
                currentProtocol = protocol
                return DetectionResult.Success(protocol)
            }
        }

        println("[ProtocolDetector] ✗ No se detectó protocolo compatible.")
        return DetectionResult.Failure(
            "No se pudo detectar un protocolo compatible. " +
            "Verifique que el motor esté encendido y el conector OBD2 bien insertado."
        )
    }

    /**
     * Intenta comunicarse usando [protocol] y valida con PID 0100.
     *
     * @return `true` si el vehículo respondió positivamente.
     */
    suspend fun tryProtocol(protocol: OBD2Protocol): Boolean {
        println("[ProtocolDetector] Probando: ${protocol.protocolName} (AT SP ${protocol.code})…")
        connection.send(protocol.atCommand)
        delay(300)
        val ok = testConnection()
        if (ok) {
            println("[ProtocolDetector] ✓ Conexión establecida con ${protocol.protocolName}")
        } else {
            println("[ProtocolDetector] ✗ Sin respuesta para ${protocol.protocolName}")
        }
        return ok
    }

    /**
     * Envía PID 0100 y verifica que la respuesta contenga "41 00".
     *
     * @return `true` si la respuesta es válida.
     */
    suspend fun testConnection(): Boolean {
        val response = connection.send("0100")
        println("[ProtocolDetector] testConnection → \"$response\"")
        val clean = response.replace(" ", "").uppercase()
        return clean.contains("4100")
    }

    /**
     * Interpreta la respuesta del comando "AT DP" o "AT DPN" devuelta por el ELM327
     * y la convierte en un [OBD2Protocol].
     *
     * Ejemplos de respuestas esperadas:
     * - "6" o "A6" → CAN_11BIT_500K
     * - "ISO 15765-4 (CAN 11/500)" → CAN_11BIT_500K
     * - "AUTO" → AUTO
     *
     * @param response Cadena cruda devuelta por el chip.
     * @return El [OBD2Protocol] correspondiente, o [OBD2Protocol.AUTO] si no se reconoce.
     */
    fun parseProtocol(response: String): OBD2Protocol {
        val clean = response.trim().uppercase()
        println("[ProtocolDetector] parseProtocol ← \"$clean\"")

        // Respuesta numérica directa de AT DPN (puede tener prefijo 'A' en auto-mode)
        val numericStr = clean.replace(Regex("[^0-9A-Fa-f]"), "")
        numericStr.toIntOrNull()?.let { return OBD2Protocol.fromId(it) }

        // Delegamos al companion que maneja texto largo (AT DP)
        return OBD2Protocol.parseFromDpResponse(response)
    }

    /** Devuelve el protocolo detectado en la última llamada a [autoDetect], o null. */
    fun getCurrentProtocol(): OBD2Protocol? = currentProtocol

    // ── Privados ──────────────────────────────────────────────────────────────

    private suspend fun initializeElm() {
        println("[ProtocolDetector] Inicializando ELM327…")
        connection.send(ATCommand.RESET)
        delay(500)
        connection.send(ATCommand.ECHO_OFF)
        delay(100)
        connection.send(ATCommand.SPACES_OFF)
        delay(100)
        connection.send(ATCommand.HEADERS_ON)
        delay(100)
        connection.send(ATCommand.LINE_FEED_ON)
        delay(100)
        println("[ProtocolDetector] ELM327 inicializado.")
    }
}
