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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import com.obelus.data.security.PasswordSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Job
import com.obelus.data.repository.ObdRepository
import com.obelus.data.repository.TelemetryRepository

data class SseClient(
    val stream: PipedOutputStream
)
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
    private val telemetryRepository: TelemetryRepository,
    private val sessionManager: PasswordSessionManager
) : NanoHttpdServer.WebDataProvider {

    private var server: NanoHttpdServer? = null
    
    private val _state = MutableStateFlow<WebServerState>(WebServerState.Stopped)
    val state: StateFlow<WebServerState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sseClients = CopyOnWriteArrayList<SseClient>()
    private var broadcastJob: Job? = null

    // Helper functions for UI
    suspend fun copyNewPassword(): String {
        return sessionManager.generateAndStoreNewPassword()
    }
    
    suspend fun invalidateCurrentPassword() {
        sessionManager.invalidateCurrentPassword()
    }
    
    suspend fun getRemainingMinutes(): Int {
        return sessionManager.getRemainingMinutes()
    }

    fun startServer(port: Int = 8080) {
        if (_state.value is WebServerState.Running) return

        try {
            val ip = getLocalIpAddress()
            if (ip == null) {
                _state.value = WebServerState.Error("Conecta a WiFi primero")
                return
            }

            server = NanoHttpdServer(context, port, this, sessionManager)
            server?.start(5000)
            _state.value = WebServerState.Running("http://$ip:$port")
            startStatusBroadcaster()
        } catch (e: Exception) {
            _state.value = WebServerState.Error(e.message ?: "Error al iniciar servidor")
        }
    }

    fun stopServer() {
        broadcastJob?.cancel()
        
        try {
            sseClients.forEach { 
                try { it.stream.close() } catch (e: Exception) {} 
            }
            sseClients.clear()
        } catch (e: Exception) {
            println("Error closing client SSE ports: ${e.message}")
        }
        
        server?.stop()
        server = null
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

    private fun startStatusBroadcaster() {
        broadcastJob?.cancel()
        broadcastJob = scope.launch {
            while (isActive) {
                val isStandby = sseClients.isEmpty()
                delay(if (isStandby) 250 else 100)
                
                try {
                    val status = getAggregatedStatusJson()
                    broadcastStatus(status)
                } catch (e: Exception) {
                    println("SSE Broadcast tick error: ${e.message}")
                }
            }
        }
    }

    private fun broadcastStatus(eventData: String) {
        if (sseClients.isNotEmpty()) {
            val message = "data: $eventData\n\n".toByteArray(Charsets.UTF_8)
            val deadStreams = mutableListOf<SseClient>()
            
            for (client in sseClients) {
                try {
                    client.stream.write(message)
                    client.stream.flush()
                } catch (e: Exception) {
                    deadStreams.add(client)
                }
            }
            
            sseClients.removeAll(deadStreams)
            deadStreams.forEach { try { it.stream.close() } catch (ex: Exception) {} }
        }
    }

    override fun subscribeToEvents(): InputStream {
        val inputStream = PipedInputStream()
        val outputStream = PipedOutputStream(inputStream)
        
        try {
            outputStream.write(": ok\n\n".toByteArray(Charsets.UTF_8))
            outputStream.flush()
        } catch (e: Exception) {}

        sseClients.add(SseClient(stream = outputStream))
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
