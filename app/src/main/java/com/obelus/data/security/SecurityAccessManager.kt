package com.obelus.data.security

import android.util.Log
import com.obelus.data.local.dao.SecurityAccessDao
import com.obelus.data.local.entity.SecurityAccessLog
import com.obelus.data.repository.ObdRepository
import com.obelus.domain.model.EcuBrand
import com.obelus.domain.model.SecuritySession
import com.obelus.domain.model.SecurityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityAccessManager @Inject constructor(
    private val obdRepository: ObdRepository,
    private val securityAccessDao: SecurityAccessDao
) {
    private val TAG = "SecurityAccessManager"
    
    private val _session = MutableStateFlow(SecuritySession(EcuBrand.GENERIC))
    val session: StateFlow<SecuritySession> = _session.asStateFlow()

    fun setBrand(brand: EcuBrand) {
        _session.value = _session.value.copy(brand = brand, state = SecurityState.LOCKED)
    }

    suspend fun requestSeed(level: Int = 0x01) {
        if (!obdRepository.isConnected()) return
        
        _session.value = _session.value.copy(state = SecurityState.SEED_REQUESTED)
        
        try {
            // Standard UDS Service 0x27 (Security Access) - Request Seed
            // Request seed level is usually odd (0x01, 0x03, etc.)
            val cmd = "27 ${String.format("%02X", level)}"
            val response = obdRepository.sendCommand(cmd)
            
            if (response.startsWith("67")) {
                val seed = response.substring(6).replace(" ", "")
                val key = calculateKey(seed, _session.value.brand)
                _session.value = _session.value.copy(
                    state = SecurityState.KEY_CALCULATED,
                    seed = seed,
                    calculatedKey = key
                )
            } else {
                handleError("Seed Request Denied: $response")
            }
        } catch (e: Exception) {
            handleError(e.message ?: "Unknown error")
        }
    }

    suspend fun sendKey(manualKey: String? = null) {
        val key = manualKey ?: _session.value.calculatedKey
        if (key.isBlank()) return

        try {
            // Level for Sending Key is usually seed_level + 1
            val cmd = "27 02 $key"
            val response = obdRepository.sendCommand(cmd)
            
            if (response.startsWith("67 02")) {
                _session.value = _session.value.copy(
                    state = SecurityState.UNLOCKED,
                    lastUnlockTime = System.currentTimeMillis()
                )
                logAccess(true)
            } else {
                handleError("Unlock Failed: $response")
                logAccess(false, response)
            }
        } catch (e: Exception) {
            handleError(e.message ?: "Unknown error")
        }
    }

    private fun calculateKey(seed: String, brand: EcuBrand): String {
        if (seed.isBlank()) return ""
        
        return when (brand) {
            EcuBrand.RENAULT -> calculateRenault(seed)
            EcuBrand.VAG -> calculateVag(seed)
            EcuBrand.BMW -> calculateBmw(seed)
            else -> seed // Fallback or generic
        }
    }

    // --- ALGORITMOS POR MARCA ---

    private fun calculateRenault(seed: String): String {
        // Simple XOR + Shift logic often found in Renault
        val seedInt = seed.toLong(16)
        val key = (seedInt xor 0x55AA55AA) shl 1
        return key.toString(16).uppercase().takeLast(8)
    }

    private fun calculateVag(seed: String): String {
        // Simplified VAG algo example
        val seedInt = seed.toLong(16)
        val key = (seedInt + 0x1234) xor 0xABCD
        return key.toString(16).uppercase().takeLast(8)
    }

    private fun calculateBmw(seed: String): String {
        // BMW often uses complex CRC or shifting
        return seed.reversed() // Placeholder for actual algo
    }

    private fun handleError(msg: String) {
        _session.value = _session.value.copy(state = SecurityState.ERROR, errorMessage = msg)
        Log.e(TAG, msg)
    }

    private suspend fun logAccess(success: Boolean, details: String? = null) {
        val current = _session.value
        securityAccessDao.insertLog(
            SecurityAccessLog(
                ecuName = "ACTIVE_ECU",
                brand = current.brand.name,
                algorithm = current.brand.name,
                isSuccess = success,
                vin = "PENDING", // Should get from identity tracer
                details = details
            )
        )
    }
}
