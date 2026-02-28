package com.obelus.protocol

import kotlinx.coroutines.flow.StateFlow

/**
 * States for the ELM327 connection.
 */
enum class ConnectionState {
    DISCONNECTED, 
    CONNECTING, 
    CONNECTED, 
    ERROR
}

/**
 * Base interface for communicating with an ELM327 interface.
 */
interface ElmConnection {
    val connectionState: StateFlow<ConnectionState>
    
    suspend fun connect(deviceAddress: String): Boolean
    suspend fun disconnect()
    suspend fun send(command: String): String
    suspend fun reconnect()
    fun isConnected(): Boolean
}
