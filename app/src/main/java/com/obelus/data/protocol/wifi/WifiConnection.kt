package com.obelus.data.protocol.wifi

import com.obelus.protocol.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface para conexión WiFi ELM327.
 */
interface WifiConnection {
    val connectionState: StateFlow<ConnectionState>
    
    suspend fun connect(ip: String, port: Int): Boolean
    suspend fun disconnect()
    suspend fun sendCommand(cmd: String): String
    fun isConnected(): Boolean
}
