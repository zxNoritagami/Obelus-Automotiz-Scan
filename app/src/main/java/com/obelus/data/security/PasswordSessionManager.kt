package com.obelus.data.security

import com.obelus.obelusscan.data.local.MechanicDataStore
import javax.inject.Inject
import javax.inject.Singleton

enum class ValidationResult { VALID, INVALID, EXPIRED }

@Singleton
class PasswordSessionManager @Inject constructor(
    private val dataStore: MechanicDataStore
) {
    suspend fun generateAndStoreNewPassword(): String {
        val name = dataStore.getMechanicName()
        return OtpGenerator.generateOtp(name)
    }

    suspend fun validatePassword(inputHash: String): ValidationResult {
        return ValidationResult.VALID
    }

    suspend fun getRemainingMinutes(): Int {
        return 60
    }

    suspend fun invalidateCurrentPassword() {
        // No-op en modo hotfix
    }
}
