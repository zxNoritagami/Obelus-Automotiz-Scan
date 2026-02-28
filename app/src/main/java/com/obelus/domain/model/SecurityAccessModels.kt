package com.obelus.domain.model

enum class SecurityState {
    LOCKED,
    SEED_REQUESTED,
    KEY_CALCULATED,
    UNLOCKED,
    ERROR
}

enum class EcuBrand {
    RENAULT,
    VAG,
    BMW,
    GENERIC
}

data class SecuritySession(
    val brand: EcuBrand,
    val state: SecurityState = SecurityState.LOCKED,
    val seed: String = "",
    val calculatedKey: String = "",
    val lastUnlockTime: Long = 0,
    val errorMessage: String? = null
)
