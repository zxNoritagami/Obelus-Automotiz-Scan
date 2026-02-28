package com.obelus.domain.model

data class VehicleFingerprint(
    val vin: String = "NO DATA",
    val hwRef: String = "NO DATA",
    val swVersion: String = "NO DATA",
    val calId: String = "NO DATA"
)
