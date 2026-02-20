package com.obelus.data.webserver

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
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
                _state.value = WebServerState.Error("No se encontró conexión WiFi")
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
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
                .takeIf { it != "0.0.0.0" }
        } catch (e: Exception) {
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
