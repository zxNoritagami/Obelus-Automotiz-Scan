package com.obelus.obelusscan.data.protocol

import android.util.Log
import com.obelus.protocol.ElmConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UdsProtocol @Inject constructor(
    private val elmConnection: ElmConnection
) {

    companion object {
        const val SID_DIAGNOSTIC_SESSION_CONTROL = 0x10
        const val SID_ECU_RESET = 0x11
        const val SID_READ_DATA_BY_IDENTIFIER = 0x22
        const val SID_SECURITY_ACCESS = 0x27 // Not fully implemented as per requirements
        const val SID_WRITE_DATA_BY_IDENTIFIER = 0x2E

        // ISO-TP Frame Types (bit 7-4 of first byte)
        const val FRAME_TYPE_SINGLE = 0x00
        const val FRAME_TYPE_FIRST = 0x10
        const val FRAME_TYPE_CONSECUTIVE = 0x20
        const val FRAME_TYPE_FLOW_CONTROL = 0x30

        private const val TIMEOUT_MS = 5000L
        private const val MAX_RETRIES = 3
    }

    suspend fun sendUdsRequest(
        ecuId: Int, // e.g. 0x7E0
        service: Int,
        subFunction: Int? = null,
        data: ByteArray = ByteArray(0)
    ): Result<UdsResponse> {
        return withTimeout(TIMEOUT_MS) {
            var attempt = 0
            var lastError: Exception? = null

            while (attempt < MAX_RETRIES) {
                try {
                    // Set ECU Header if needed (ELM specific: AT SH xyz)
                    val headerCmd = "AT SH " + Integer.toHexString(ecuId).uppercase()
                    elmConnection.send(headerCmd)

                    // Build Payload
                    val payload = mutableListOf<Byte>()
                    payload.add(service.toByte())
                    if (subFunction != null) {
                        payload.add(subFunction.toByte())
                    }
                    payload.addAll(data.toList())

                    // Send Request
                    val responseBytes = sendIsoTpFrames(payload.toByteArray())
                    
                    // Parse Response
                    val response = parseUdsResponse(service, responseBytes)
                    return@withTimeout Result.success(response)

                } catch (e: Exception) {
                    lastError = e
                    Log.e("UdsProtocol", "Attempt ${attempt + 1} failed: ${e.message}")
                    attempt++
                    delay(200) // Wait before retry
                }
            }
            Result.failure(lastError ?: Exception("Unknown UDS error"))
        }
    }

    private suspend fun sendIsoTpFrames(payload: ByteArray): ByteArray {
        // Simple implementation assuming ELM327 handles segmentation for requests < 7 bytes
        // and standard multi-frame for responses.
        // For larger requests, we would need to manually segment into FirstFrame + ConsecutiveFrames.
        
        val hexPayload = payload.joinToString("") { "%02X".format(it) }
        
        // Send to ELM
        // ELM327 automatically generates ISO-TP frames for standard standard CAN (ISO 15765)
        // We just send the data bytes.
        val rawResponse = elmConnection.send(hexPayload)
        
        // Parse the ELM response (which might be multi-line)
        return parseElmResponseToBytes(rawResponse)
    }

    private fun parseElmResponseToBytes(rawResponse: String): ByteArray {
        // Clean response
        val cleanResponse = rawResponse.replace(">", "")
            .replace("\\s".toRegex(), "")
            .uppercase()

        if (cleanResponse.contains("NODATA") || cleanResponse.contains("ERROR")) {
            throw Exception("ELM Error: $cleanResponse")
        }

        // Basic hex parsing
        // NOTE: In a robust implementation we would parse 
        // 0: 62 10 01 ...
        // 1: ...
        // Logic to strip sequence numbers if present.
        
        // Attempt to parse continuous hex string
        return try {
            cleanResponse.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            throw Exception("Failed to parse response bytes: $cleanResponse")
        }
    }

    private fun parseUdsResponse(requestService: Int, responseBytes: ByteArray): UdsResponse {
        if (responseBytes.isEmpty()) throw Exception("Empty response")

        val responseService = responseBytes[0].toInt() and 0xFF
        
        // Check for Negative Response (7F)
        if (responseService == 0x7F) {
            val failedService = responseBytes.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
            val nrc = responseBytes.getOrNull(2)?.toInt()?.and(0xFF) ?: 0
            throw UdsNegativeResponseException(failedService, nrc)
        }

        // Check for Positive Response (Service + 0x40)
        if (responseService != (requestService + 0x40)) {
            throw Exception("Unexpected response service: ${Integer.toHexString(responseService)}")
        }

        // Extract data
        val data = responseBytes.copyOfRange(1, responseBytes.size)
        return UdsResponse(responseService, data)
    }
    
    // --- Helper for DTC Parsing ---
    
    fun parseDtcUds(dtcHigh: Byte, dtcLow: Byte, status: Byte): UdsDtc {
        // Format: Pxxxx-xx
        val dtcRaw = ((dtcHigh.toInt() and 0xFF) shl 8) or (dtcLow.toInt() and 0xFF)
        val code = convertToDtcString(dtcRaw)
        return UdsDtc(code, status.toInt() and 0xFF)
    }

    private fun convertToDtcString(raw: Int): String {
        val type = (raw and 0xC000) shr 14
        val prefix = when(type) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            3 -> "U"
            else -> "?"
        }
        val num = raw and 0x3FFF
        return String.format("%s%04X", prefix, num)
    }
}

data class UdsResponse(
    val serviceId: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UdsResponse

        if (serviceId != other.serviceId) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = serviceId
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class UdsDtc(
    val code: String,
    val status: Int
)

class UdsNegativeResponseException(val failedService: Int, val nrc: Int) : Exception("UDS NACK: Service ${Integer.toHexString(failedService)} RC: ${Integer.toHexString(nrc)}")
