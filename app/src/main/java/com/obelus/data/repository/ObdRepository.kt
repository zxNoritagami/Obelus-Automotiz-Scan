package com.obelus.data.repository

import com.obelus.data.obd2.Obd2Decoder
import com.obelus.data.obd2.ObdReading
import com.obelus.data.obd2.PidDefinition
import com.obelus.data.local.entity.RaceRecord
import com.obelus.data.protocol.wifi.WifiConnection
import com.obelus.protocol.ConnectionState
import com.obelus.protocol.ElmConnection
import com.obelus.protocol.usb.UsbElmConnection
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Named

/** Selects which physical channel to use for OBD2 communication. */
enum class ConnectionType { BLUETOOTH, USB, WIFI }

interface ObdRepository {
    val connectionState: StateFlow<ConnectionState>

    fun setConnectionType(type: ConnectionType)
    suspend fun connect(deviceAddress: String): Boolean
    // Add specifically for WiFi since it requires ip + port
    suspend fun connectWifi(ip: String, port: Int): Boolean
    suspend fun disconnect()
    fun isConnected(): Boolean
    suspend fun requestPid(pid: String): ObdReading?
    fun getSupportedPids(): List<PidDefinition>
    suspend fun sendCommand(command: String): String
    suspend fun getRaceHistory(): List<RaceRecord>
}

class ObdRepositoryImpl @Inject constructor(
    @Named("bluetooth") private val bluetoothConnection: ElmConnection,
    private val usbConnection: UsbElmConnection,
    private val wifiConnection: WifiConnection
) : ObdRepository {

    private var activeType: ConnectionType = ConnectionType.BLUETOOTH

    override val connectionState: StateFlow<ConnectionState>
        get() = when (activeType) {
            ConnectionType.USB -> usbConnection.connectionState
            ConnectionType.WIFI -> wifiConnection.connectionState
            else -> bluetoothConnection.connectionState
        }

    override fun setConnectionType(type: ConnectionType) {
        activeType = type
    }

    override suspend fun connect(deviceAddress: String): Boolean =
        when (activeType) {
            ConnectionType.USB -> usbConnection.connect(deviceAddress)
            ConnectionType.WIFI -> false // Use connectWifi for WIFI
            else -> bluetoothConnection.connect(deviceAddress)
        }

    override suspend fun connectWifi(ip: String, port: Int): Boolean =
        if (activeType == ConnectionType.WIFI) wifiConnection.connect(ip, port) else false

    override suspend fun disconnect() =
        when (activeType) {
            ConnectionType.USB -> usbConnection.disconnect()
            ConnectionType.WIFI -> wifiConnection.disconnect()
            else -> bluetoothConnection.disconnect()
        }

    override fun isConnected(): Boolean =
        when (activeType) {
            ConnectionType.USB -> usbConnection.isConnected()
            ConnectionType.WIFI -> wifiConnection.isConnected()
            else -> bluetoothConnection.isConnected()
        }

    override suspend fun requestPid(pid: String): ObdReading? {
        if (!isConnected()) return null
        return try {
            val rawResponse = sendCommand("01$pid")
            Obd2Decoder.decode(rawResponse)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getSupportedPids(): List<PidDefinition> =
        Obd2Decoder.SUPPORTED_PIDS.values.toList()

    override suspend fun sendCommand(command: String): String =
        when (activeType) {
            ConnectionType.USB -> if (usbConnection.isConnected()) usbConnection.send(command) else "NO DATA"
            ConnectionType.WIFI -> if (wifiConnection.isConnected()) wifiConnection.sendCommand(command) else "NO DATA"
            else -> if (bluetoothConnection.isConnected()) bluetoothConnection.send(command) else "NO DATA"
        }

    override suspend fun getRaceHistory(): List<RaceRecord> {
        // Fallback simuation for UI
        return listOf(
            RaceRecord(id = 1, timestamp = System.currentTimeMillis() - 86400000, type = "0-100 km/h", timeMs = 4250, topSpeed = 105.0),
            RaceRecord(id = 2, timestamp = System.currentTimeMillis() - 172800000, type = "0-200 km/h", timeMs = 12400, topSpeed = 202.0),
            RaceRecord(id = 3, timestamp = System.currentTimeMillis() - 259200000, type = "1/4 Milla", timeMs = 11800, topSpeed = 185.0)
        )
    }
}
