package com.obelus.data.webserver

data class ClientInfo(
    val ip: String,
    val connectedAt: Long
)

data class SecurityContext(
    val networkName: String = "Desconocida",
    val serverUrl: String = "",
    val rangeMeters: Int = 50,
    val connectedClients: Int = 0,
    val clientList: List<ClientInfo> = emptyList(),
    val isObdConnected: Boolean = false,
    val idleMinutesRemaining: Int = 5,
    val autoStopEnabled: Boolean = true
)
