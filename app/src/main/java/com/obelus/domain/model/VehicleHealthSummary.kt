package com.obelus.domain.model

data class VehicleHealthSummary(
    val fingerprint: VehicleFingerprint = VehicleFingerprint(),
    val ecusFound: List<DdtEcu> = emptyList(),
    val dtcsByEcu: Map<String, List<String>> = emptyMap(),
    val batteryVoltage: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
