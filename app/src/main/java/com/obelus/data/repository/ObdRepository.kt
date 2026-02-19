package com.obelus.data.repository

import com.obelus.data.obd2.Obd2Decoder
import com.obelus.data.obd2.ObdReading
import com.obelus.data.obd2.PidDefinition
import com.obelus.protocol.ElmConnection
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

interface ObdRepository {
    val connectionState: StateFlow<com.obelus.protocol.ConnectionState>
    
    suspend fun connect(deviceAddress: String): Boolean
    suspend fun disconnect()
    fun isConnected(): Boolean
    suspend fun requestPid(pid: String): ObdReading?
    fun getSupportedPids(): List<PidDefinition>
    suspend fun sendCommand(command: String): String
}

class ObdRepositoryImpl @Inject constructor(
    private val elmConnection: ElmConnection
) : ObdRepository {

    override val connectionState = elmConnection.connectionState

    override suspend fun connect(deviceAddress: String): Boolean {
        return elmConnection.connect(deviceAddress)
    }

    override suspend fun disconnect() {
        elmConnection.disconnect()
    }

    override fun isConnected(): Boolean {
        return elmConnection.isConnected()
    }

    override suspend fun requestPid(pid: String): ObdReading? {
        if (!elmConnection.isConnected()) return null

        return try {
            // Mode 01 + PID, e.g., "010C"
            val command = "01$pid"
            val rawResponse = elmConnection.send(command)
            
            // Decode
            Obd2Decoder.decode(rawResponse)
        } catch (e: Exception) {
            e.printStackTrace() // Log error
            null
        }
    }

    override fun getSupportedPids(): List<PidDefinition> {
        return Obd2Decoder.SUPPORTED_PIDS.values.toList()
    }

    override suspend fun sendCommand(command: String): String {
        return if (elmConnection.isConnected()) {
            elmConnection.send(command)
        } else {
            "NO DATA"
        }
    }
}
