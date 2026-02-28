package com.obelus.domain.usecase

import com.obelus.data.repository.ObdRepository
import com.obelus.domain.model.DdtEcu
import com.obelus.domain.model.VehicleHealthSummary
import kotlinx.coroutines.delay
import javax.inject.Inject

class GatherVehicleHealthUseCase @Inject constructor(
    private val obdRepository: ObdRepository,
    private val identityExtractor: VehicleIdentityExtractor
) {
    suspend fun execute(availableEcus: List<DdtEcu>): VehicleHealthSummary {
        if (!obdRepository.isConnected()) return VehicleHealthSummary()

        // 1. SYSTEM IDENTITY TRACER: Get vehicle fingerprint (with caching support)
        val fingerprint = identityExtractor.getFingerprint()

        // 2. Get Battery Voltage
        val voltageRaw = obdRepository.sendCommand("AT RV")
        val voltage = voltageRaw.replace("V", "").toFloatOrNull() ?: 0f

        // 3. Auto-Scan for ECUs and their DTCs
        val foundEcus = mutableListOf<DdtEcu>()
        val dtcsByEcu = mutableMapOf<String, List<String>>()

        // Common diagnostic addresses
        val commonAddresses = listOf("7A", "01", "04", "26", "51", "13")
        
        for (addr in commonAddresses) {
            obdRepository.sendCommand("AT SH $addr")
            val idResponse = obdRepository.sendCommand("22 F1 A0")
            
            if (idResponse.isNotEmpty() && !idResponse.contains("ERROR")) {
                val match = availableEcus.find { it.name.contains(addr, ignoreCase = true) }
                if (match != null) {
                    foundEcus.add(match)
                    // Request DTCs (UDS Service 19 02)
                    val dtcResponse = obdRepository.sendCommand("19 02 AF")
                    dtcsByEcu[match.name] = parseDtcs(dtcResponse)
                }
            }
            delay(100) // Small delay between ECU scans to maintain stability
        }

        return VehicleHealthSummary(
            fingerprint = fingerprint,
            ecusFound = foundEcus,
            dtcsByEcu = dtcsByEcu,
            batteryVoltage = voltage
        )
    }

    private fun parseDtcs(raw: String): List<String> {
        if (raw.contains("NO DATA") || raw.contains("ERROR")) return emptyList()
        val clean = raw.replace(" ", "").drop(6)
        if (clean.length < 6) return emptyList()
        return clean.chunked(6).map { "P" + it.take(4) }
    }
}
