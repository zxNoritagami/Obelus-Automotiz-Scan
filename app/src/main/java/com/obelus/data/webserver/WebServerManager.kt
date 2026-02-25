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

sealed class WebServerState {
    object Stopped : WebServerState()
    data class Running(val url: String) : WebServerState()
    data class Error(val message: String) : WebServerState()
}

@Singleton
class WebServerManager @Inject constructor(
    @ApplicationContext private val context: Context
) : NanoHttpdServer.WebDataProvider {

    private var server: NanoHttpdServer? = null
    
    private val _state = MutableStateFlow<WebServerState>(WebServerState.Stopped)
    val state: StateFlow<WebServerState> = _state.asStateFlow()

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
        } catch (e: Exception) {
            _state.value = WebServerState.Error(e.message ?: "Error al iniciar servidor")
        }
    }

    fun stopServer() {
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

    // --- Data Provider Impl (Simulado por ahora para compilación) ---

    override fun getSystemStatusJson(): String {
        return JSONObject().apply {
            put("status", "connected")
            put("voltage", 12.6)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    override fun getSensorsJson(): String {
        return JSONObject().apply {
            put("rpm", 850)
            put("speed", 0)
            put("temp", 92)
        }.toString()
    }

    override fun getDtcJson(): String = "[]"
    override fun getRaceDataJson(): String = "{}"
}
