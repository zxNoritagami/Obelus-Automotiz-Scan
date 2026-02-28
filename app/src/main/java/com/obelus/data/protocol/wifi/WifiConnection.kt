package com.obelus.data.protocol.wifi

import com.obelus.protocol.ConnectionState
import com.obelus.protocol.ElmConnection
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface para conexión WiFi ELM327.
 */
interface WifiConnection : ElmConnection {
    override val connectionState: StateFlow<ConnectionState>
    
    suspend fun connect(ip: String, port: Int): Boolean
    override suspend fun disconnect()
    override suspend fun send(command: String): String
    override suspend fun reconnect()
    override fun isConnected(): Boolean
}
