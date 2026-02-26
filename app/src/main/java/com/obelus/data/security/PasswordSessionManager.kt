package com.obelus.data.security

import com.obelus.obelusscan.data.local.MechanicDataStore
import javax.inject.Inject
import javax.inject.Singleton

enum class ValidationResult {
    VALID, INVALID, EXPIRED
}

@Singleton
class PasswordSessionManager @Inject constructor(
    private val dataStore: MechanicDataStore
) {
    suspend fun generateAndStoreNewPassword(): String {
        val name = dataStore.getMechanicName()
        val hash = OtpGenerator.generateOtp(name)
        val now = System.currentTimeMillis()
        
        dataStore.setCurrentHash(hash, now)
        return hash
    }

    suspend fun validatePassword(inputHash: String): ValidationResult {
        val storedHash = dataStore.getCurrentHash()
        val timestamp = dataStore.getHashTimestamp()
        val now = System.currentTimeMillis()

        if (storedHash == null || storedHash.isEmpty() || timestamp == 0L) {
            return ValidationResult.EXPIRED
        }

        if (inputHash != storedHash) {
            return ValidationResult.INVALID
        }

        val elapsedMinutes = (now - timestamp) / (60 * 1000)
        if (elapsedMinutes >= MechanicDataStore.HASH_EXPIRY_MINUTES) {
            dataStore.clearHash()
            return ValidationResult.EXPIRED
        }

        return ValidationResult.VALID
    }

    suspend fun getRemainingMinutes(): Int {
        val storedHash = dataStore.getCurrentHash()
        val timestamp = dataStore.getHashTimestamp()
        
        if (storedHash.isNullOrEmpty() || timestamp == 0L) {
            return 0
        }

        val elapsedMinutes = (System.currentTimeMillis() - timestamp) / (60 * 1000).toInt()
        val remaining = MechanicDataStore.HASH_EXPIRY_MINUTES - elapsedMinutes
        
        if (remaining <= 0) {
            dataStore.clearHash()
            return 0
        }
        
        return remaining.toInt()
    }

    suspend fun invalidateCurrentPassword() {
        dataStore.clearHash()
    }
}
