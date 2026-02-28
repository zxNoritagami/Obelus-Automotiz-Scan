package com.obelus.domain.usecase

import com.obelus.data.repository.ObdRepository
import com.obelus.domain.model.VehicleFingerprint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleIdentityExtractor @Inject constructor(
    private val obdRepository: ObdRepository
) {
    private var cachedFingerprint: VehicleFingerprint? = null

    suspend fun getFingerprint(forceRefresh: Boolean = false): VehicleFingerprint {
        if (!forceRefresh && cachedFingerprint != null) {
            return cachedFingerprint!!
        }

        val vin = queryId("09 02", true) // Standard VIN
        val hwRef = queryId("22 F1 91", true) // Hardware Ref
        val swVersion = queryId("22 F1 94", true) // SW Version
        val calId = queryId("22 F1 95", true) // Calibration ID

        val fingerprint = VehicleFingerprint(
            vin = if (vin.isNotBlank()) vin else "NO DATA",
            hwRef = if (hwRef.isNotBlank()) hwRef else "NO DATA",
            swVersion = if (swVersion.isNotBlank()) swVersion else "NO DATA",
            calId = if (calId.isNotBlank()) calId else "NO DATA"
        )

        cachedFingerprint = fingerprint
        return fingerprint
    }

    private suspend fun queryId(command: String, isAscii: Boolean): String {
        return try {
            val response = obdRepository.sendCommand(command)
            if (response.contains("ERROR") || response.contains("NO DATA") || response.isBlank()) {
                return ""
            }
            
            if (isAscii) {
                parseAsciiResponse(response)
            } else {
                response
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseAsciiResponse(raw: String): String {
        return try {
            // Clean response: remove headers (usually first 3 bytes / 6 chars)
            val cleanHex = raw.replace(" ", "")
            if (cleanHex.length <= 6) return ""
            
            val dataHex = cleanHex.substring(6)
            dataHex.chunked(2)
                .mapNotNull { 
                    try { it.toInt(16).toChar() } catch (e: Exception) { null }
                }
                .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                .joinToString("")
                .trim()
        } catch (e: Exception) {
            ""
        }
    }
}
