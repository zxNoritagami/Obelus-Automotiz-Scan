package com.obelus.protocol

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.obelus.protocol.utils.toHex
import com.obelus.protocol.utils.hexToBytes
import com.obelus.protocol.utils.toHexCompact

// ─────────────────────────────────────────────────────────────────────────────
// IsoTPHandler.kt
// Maneja mensajes multi-frame según ISO 15765-2 (ISO-TP) sobre ELM327.
// Pure Kotlin – sin dependencias de Android.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Implementa la capa de transporte ISO 15765-2 (ISO-TP) para comunicacíon
 * con las ECUs a través de un [ElmConnection].
 *
 * Tipos de trama soportados
 * - **SF**  – Single Frame    (PCI nibble alto = 0x0)
 * - **FF**  – First Frame     (PCI nibble alto = 0x1)
 * - **CF**  – Consecutive Frame (PCI nibble alto = 0x2)
 * - **FC**  – Flow Control generado por nosotros al detectar FF
 *
 * @param connection Implementación activa de [ElmConnection].
 */
class IsoTPHandler(private val connection: ElmConnection) {

    companion object {
        private const val TAG               = "IsoTPHandler"
        private const val FRAME_TIMEOUT_MS  = 2000L
        private const val FC_FRAME          = "30 00 0A"   // ContinueToSend, BS=0, STmin=10ms
        private const val MAX_CF_WAIT_MS    = 1000L
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Sobrecarga de alto nivel que acepta bytes crudos de payload, configura
     * los headers CAN de TX/RX y devuelve la respuesta ensamblada como [ByteArray].
     *
     * @param request Bytes a enviar como payload ISO-TP (ej.: 0x22, 0xF1, 0x90 para leer VIN UDS).
     * @param txId    Identificador CAN de transmisión (ej. 0x7E0 para motor).
     * @param rxId    Identificador CAN de recepción   (ej. 0x7E8 para ECU motor).
     * @return        Payload ensamblado o [ByteArray] vacío en caso de error/timeout.
     */
    suspend fun sendAndReceive(request: ByteArray, txId: Int, rxId: Int): ByteArray {
        val txIdHex = "%03X".format(txId)
        val rxIdHex = "%03X".format(rxId)

        println("[IsoTPHandler] sendAndReceive txId=$txIdHex rxId=$rxIdHex payload=${request.toHex()}")

        // Configurar headers CAN
        connection.send("${ATCommand.SET_HEADER}$txIdHex")
        delay(50)
        connection.send("${ATCommand.SET_RECEIVE_FILTER}$rxIdHex")
        delay(50)

        // Construir el comando en formato hexadecimal y enviarlo
        val commandHex = request.toHexCompact()
        val rawResponse = withTimeoutOrNull(FRAME_TIMEOUT_MS) {
            connection.send(commandHex)
        } ?: run {
            println("[IsoTPHandler] Timeout esperando respuesta del ELM327.")
            return byteArrayOf()
        }

        println("[IsoTPHandler] Respuesta cruda: \"$rawResponse\"")
        return receiveMultiFrame(rawResponse)
    }

    /**
     * Versión string-based que acepta el comando ya formateado (compatible con código legado).
     *
     * @param command Comando hexadecimal como string (ej. "09 02" para VIN).
     * @param txId    Header CAN TX como string (ej. "7E0"), null para no cambiar.
     * @param rxId    Filtro CAN RX como string (ej. "7E8"), null para no cambiar.
     * @return        Payload ensamblado como string hexadecimal.
     */
    suspend fun sendAndReceive(command: String, txId: String? = null, rxId: String? = null): String {
        txId?.let {
            connection.send("${ATCommand.SET_HEADER}$it")
            delay(50)
        }
        rxId?.let {
            connection.send("${ATCommand.SET_RECEIVE_FILTER}$it")
            delay(50)
        }

        val rawResponse = withTimeoutOrNull(FRAME_TIMEOUT_MS) {
            connection.send(command)
        } ?: return ""

        return receiveMultiFrame(rawResponse).toHex()
    }

    // ── Ensamblaje de tramas ──────────────────────────────────────────────────

    /**
     * Hub de ensamblaje: decide si la respuesta es SF, FF/CF o respuesta OBD2 directa.
     *
     * @param rawResponse Primera línea devuelta por el ELM327.
     * @return Payload ensamblado como [ByteArray].
     */
    suspend fun receiveMultiFrame(rawResponse: String): ByteArray {
        // Normalizar: quitar espacios y retornos de carro
        val clean = rawResponse
            .trim()
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .uppercase()

        if (clean.isEmpty() || clean == "NODATA" || clean == "?") {
            println("[IsoTPHandler] Respuesta vacía o NO DATA.")
            return byteArrayOf()
        }

        // Determinar tipo de trama por el primer nibble del PCI
        val pciNibble = clean.firstOrNull()?.digitToIntOrNull(16) ?: return byteArrayOf()

        return when (pciNibble) {
            0    -> {
                println("[IsoTPHandler] → Single Frame detectado.")
                parseSingleFrame(clean)
            }
            1    -> {
                println("[IsoTPHandler] → First Frame detectado, iniciando re-ensamblado…")
                reassembleFromFirstFrame(clean)
            }
            else -> {
                // Respuesta OBD2 directa (ej. "41 00 BE 3F E8 11") sin headers visibles
                println("[IsoTPHandler] → Respuesta directa OBD2.")
                clean.hexToBytes()
            }
        }
    }

    /**
     * Parsea un Single Frame ISO-TP.
     *
     * Formato: `0L DD DD DD DD DD DD DD` (L = longitud de datos en bytes)
     *
     * @param frame String hexadecimal sin espacios.
     */
    fun parseSingleFrame(frame: String): ByteArray {
        if (frame.length < 2) return byteArrayOf()
        val dataLength = frame.substring(1, 2).toInt(16)
        val dataStart  = 2
        val dataEnd    = dataStart + dataLength * 2

        return if (frame.length >= dataEnd) {
            val hex = frame.substring(dataStart, dataEnd)
            println("[IsoTPHandler] SF – $dataLength bytes: $hex")
            hex.hexToBytes()
        } else {
            println("[IsoTPHandler] SF – longitud inválida, devolviendo bytes disponibles.")
            frame.substring(dataStart).hexToBytes()
        }
    }

    /**
     * Re-ensambla un mensaje multi-frame comenzando desde el First Frame (FF).
     *
     * El handler envía automáticamente un Flow Control (FC) con ContinueToSend,
     * BlockSize=0 y STmin=10ms para que la ECU continúe enviando Consecutive Frames.
     *
     * @param firstFrame String hexadecimal del primer frame (sin espacios).
     */
    private suspend fun reassembleFromFirstFrame(firstFrame: String): ByteArray {
        // FF: 1LLL DDDDDDDDDDDD  (LLL = 12 bits de longitud total)
        if (firstFrame.length < 4) return byteArrayOf()
        val totalLength  = firstFrame.substring(1, 4).toInt(16)
        val firstPayload = firstFrame.substring(4) // 6 bytes = 12 chars de datos del FF

        println("[IsoTPHandler] FF – longitud total=$totalLength bytes.")

        // Enviar Flow Control para autorizar tramas consecutivas
        sendFlowControl()

        val frames = mutableListOf(firstFrame)
        var receivedBytes = firstPayload.length / 2

        while (receivedBytes < totalLength) {
            val cf = withTimeoutOrNull(MAX_CF_WAIT_MS) {
                // Leer siguiente línea del buffer (envío de "" provoca lectura)
                connection.send("")
            } ?: break

            val cleanCf = cf.trim().replace(" ", "").uppercase()
            if (cleanCf.isEmpty()) continue

            val cfNibble = cleanCf.firstOrNull()?.digitToIntOrNull(16) ?: break
            if (cfNibble != 2) {
                println("[IsoTPHandler] CF inesperado (nibble=$cfNibble): $cleanCf")
                break
            }

            frames.add(cleanCf)
            // CF: 2N DDDDDDDDDDDDDD (N=seq#, datos desde byte 2 = 7 bytes = 14 chars)
            receivedBytes += (cleanCf.length - 2) / 2
            println("[IsoTPHandler] CF recibido – acumulado=$receivedBytes/$totalLength bytes.")
        }

        return reassembleFrames(frames, totalLength)
    }

    /**
     * Envía una trama de Flow Control (FC) para indicar a la ECU que continúe
     * enviando Consecutive Frames.
     *
     * Frame estándar: `30 00 0A` (ContinueToSend, BS=0, STmin=10ms).
     */
    private suspend fun sendFlowControl() {
        println("[IsoTPHandler] Enviando Flow Control: $FC_FRAME")
        connection.send(FC_FRAME)
        delay(10)
    }

    /**
     * Re-ensambla una lista de frames (FF + CFs) en el payload completo.
     *
     * @param frames     Lista de frames hexadecimales (sin espacios). El primero es el FF.
     * @param totalBytes Longitud total esperada (del campo LLL del FF).
     * @return           Payload ensamblado truncado a [totalBytes].
     */
    fun reassembleFrames(frames: List<String>, totalBytes: Int = Int.MAX_VALUE): ByteArray {
        val sb = StringBuilder()

        frames.forEachIndexed { index, frame ->
            when {
                index == 0 && frame.firstOrNull()?.digitToIntOrNull(16) == 1 -> {
                    // First Frame: datos comienzan en nibble 4 (char 4)
                    sb.append(frame.substring(if (frame.length > 4) 4 else frame.length))
                }
                frame.firstOrNull()?.digitToIntOrNull(16) == 2 -> {
                    // Consecutive Frame: datos comienzan en char 2 (seq#)
                    sb.append(frame.substring(if (frame.length > 2) 2 else frame.length))
                }
                else -> sb.append(frame) // Caso degenerado
            }
        }

        val allBytes = sb.toString().hexToBytes()
        val result = if (allBytes.size > totalBytes) allBytes.take(totalBytes).toByteArray()
                     else allBytes
        println("[IsoTPHandler] reassembleFrames – ${result.size} bytes ensamblados: ${result.toHex()}")
        return result
    }
}
