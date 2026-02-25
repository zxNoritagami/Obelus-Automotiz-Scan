package com.obelus.data.webserver

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.net.Inet4Address
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import com.obelus.data.repository.ObdRepository
import com.obelus.data.repository.TelemetryRepository

sealed class WebServerState {
    object Stopped : WebServerState()
    data class Running(val url: String) : WebServerState()
    data class Error(val message: String) : WebServerState()
}

@Singleton
@Singleton
class WebServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val obdRepository: ObdRepository,
    private val telemetryRepository: TelemetryRepository
) : NanoHttpdServer.WebDataProvider {

    private var server: NanoHttpdServer? = null
    
    private val _state = MutableStateFlow<WebServerState>(WebServerState.Stopped)
    val state: StateFlow<WebServerState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clientStreams = CopyOnWriteArrayList<PipedOutputStream>()
    private var isBroadcasting = false

    fun startServer(port: Int = 8080) {
        if (_state.value is WebServerState.Running) return

        try {
            val ip = getLocalIpAddress()
            if (ip == null) {
                _state.value = WebServerState.Error("Conecta a WiFi primero")
                return
            }

            server = NanoHttpdServer(context, port, this)
            server?.start()
            _state.value = WebServerState.Running("http://$ip:$port")
            startSseBroadcastLoop()
            startStatusBroadcaster() // Renamed function call
        } catch (e: Exception) {
            _state.value = WebServerState.Error(e.message ?: "Error al iniciar servidor")
        }
    }

    fun stopServer() {
        broadcastJob?.cancel() // Cancel the broadcasting job
        
        // Modificacion PROMPT 13: Cerrar flujos SSE de clientes colgados
        try {
            sseClients.forEach { it.close() } // Close all client streams
            sseClients.clear()
        } catch (e: Exception) {
            println("Error closing client SSE ports: ${e.message}")
        }
        
        nanoServer?.stop() // Stop the NanoHttpd server
        nanoServer = null
        _state.value = WebServerState.Stopped
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return null
            val capabilities = cm.getNetworkCapabilities(network) ?: return null
            
            // Permitir WiFi, Ethernet o USB Tethering
            val hasValidTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB)
            
            if (!hasValidTransport) return null
            
            val linkProperties = cm.getLinkProperties(network) ?: return null
            linkProperties.linkAddresses.find { it.address is Inet4Address }?.address?.hostAddress
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- SSE Broadcasting ---

    private fun startStatusBroadcaster() { // Renamed from startSseBroadcastLoop
        broadcastJob?.cancel() // Ensure any previous job is cancelled
        broadcastJob = serviceScope.launch { // Uses serviceScope and broadcastJob
            while (isActive) {
                // Modificacion PROMPT 13: Reducir broadcast a 250ms si no hay clientes (Standby throttling)
                val isStandby = sseClients.isEmpty() // Check if there are any active clients
                delay(if (isStandby) 250 else 100) // Adjust delay based on client presence
                
                try {
                    val status = getAggregatedStatusJson() // Use existing JSON generation
                    broadcastStatus(status) // New helper function to broadcast
                } catch (e: Exception) {
                    println("SSE Broadcast tick error: ${e.message}")
                }
            }
        }
    }

    private fun broadcastStatus(eventData: String) {
        if (sseClients.isNotEmpty()) {
            // SSE format: data: {json}\n\n
            val message = "data: $eventData\n\n".toByteArray(Charsets.UTF_8)
            
            val deadStreams = mutableListOf<PipedOutputStream>()
            
            for (stream in sseClients) { // Iterate through sseClients
                try {
                    stream.write(message)
                    stream.flush()
                } catch (e: Exception) {
                    // Client disconnected or stream broken
                    deadStreams.add(stream)
                }
            }
            
            sseClients.removeAll(deadStreams) // Remove dead clients
            deadStreams.forEach { try { it.close() } catch (ex: Exception) {} } // Close their streams
        }
    }

    override fun subscribeToEvents(): InputStream {
        val inputStream = PipedInputStream()
        val outputStream = PipedOutputStream(inputStream)
        
        // Initial connection ok padding
        try {
            outputStream.write(": ok\n\n".toByteArray(Charsets.UTF_8))
            outputStream.flush()
        } catch (e: Exception) {}

        clientStreams.add(outputStream)
        return inputStream
    }

    private fun getAggregatedStatusJson(): String {
        val isConnected = obdRepository.isConnected()
        val data = if (isConnected) {
            telemetryRepository.latestTelemetryData.value
        } else null

        return JSONObject().apply {
            put("obdConnected", isConnected)
            // PIDS
            put("rpm", data?.rpm ?: 0)
            put("speed", data?.speed ?: 0)
            put("temp", data?.engineTemp ?: 0)
            // Status
            put("voltage", data?.voltage ?: 0.0)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    // --- Data Provider Impl ---

    override fun getSystemStatusJson(): String {
        return getAggregatedStatusJson()
    }

    override fun getSensorsJson(): String {
        return getAggregatedStatusJson()
    }

    override fun getDtcJson(): String = "[]"
    override fun getRaceDataJson(): String = "{}"
}
