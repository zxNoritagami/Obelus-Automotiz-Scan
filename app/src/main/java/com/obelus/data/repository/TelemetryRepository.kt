package com.obelus.data.repository

import android.util.Log
import com.obelus.mqtt.ObdMqttClient
import com.obelus.mqtt.ObdTelemetry
import com.obelus.obelusscan.data.local.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estado de publicación de telemetría MQTT.
 */
sealed class TelemetryState {
    object Idle       : TelemetryState()
    object Connecting : TelemetryState()
    object Publishing : TelemetryState()
    data class Error(val message: String) : TelemetryState()
}

/**
 * Repository encargado de tomar datos del [ObdRepository] y publicarlos
 * por MQTT cada [publishIntervalMs] milisegundos.
 *
 * - Solo publica si la telemetría está habilitada en [SettingsDataStore]
 * - Se conecta al broker configurado antes de publicar
 * - Reconexión automática delegada a [ObdMqttClient]
 * - No lanza excepciones hacia arriba; todas las errores quedan en [state]
 */
@Singleton
class TelemetryRepository @Inject constructor(
    private val mqttClient: ObdMqttClient,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "TelemetryRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var telemetryJob: Job? = null

    private val _state = MutableStateFlow<TelemetryState>(TelemetryState.Idle)
    val state: StateFlow<TelemetryState> = _state.asStateFlow()

    private val _publishedMessages = MutableStateFlow(0L)
    val publishedMessages: StateFlow<Long> = _publishedMessages.asStateFlow()

    // Snapshot mutable de la telemetría actual — actualizado por DashboardViewModel
    private var currentTelemetry = ObdTelemetry()

    /**
     * Actualiza los datos actuales de telemetría.
     * Llamado por DashboardViewModel en cada ciclo de scan.
     */
    fun updateTelemetry(telemetry: ObdTelemetry) {
        currentTelemetry = telemetry
    }

    /**
     * Inicia el loop de publicación MQTT si la telemetría está habilitada.
     * Se conecta al broker antes de publicar usando la config guardada.
     *
     * @param dtcCount Número de DTCs activos actuales
     */
    fun startTelemetry(dtcCount: Int = 0) {
        if (telemetryJob?.isActive == true) {
            Log.d(TAG, "Telemetría ya en curso, ignorando startTelemetry()")
            return
        }

        telemetryJob = scope.launch {
            try {
                // 1. Leer configuración actual
                val config = settingsDataStore.telemetryConfig
                    .catch { e -> Log.e(TAG, "Error leyendo config: ${e.message}") }
                    .first()

                if (!config.isTelemetryEnabled) {
                    Log.i(TAG, "Telemetría deshabilitada en configuración — ignorado")
                    _state.value = TelemetryState.Idle
                    return@launch
                }

                Log.i(TAG, "Iniciando telemetría → ${config.brokerUrl} (cada ${config.publishIntervalMs}ms)")
                _state.value = TelemetryState.Connecting

                // 2. Conectar al broker
                val connected = mqttClient.connect(
                    brokerUrl = config.brokerUrl,
                    clientId  = config.clientId
                )

                if (!connected) {
                    val msg = "No se pudo conectar al broker: ${config.brokerUrl}"
                    Log.e(TAG, msg)
                    _state.value = TelemetryState.Error(msg)
                    return@launch
                }

                _state.value = TelemetryState.Publishing
                Log.i(TAG, "✅ Conectado al broker. Publicando telemetría...")

                // 3. Loop de publicación
                while (isActive && mqttClient.isConnected()) {
                    try {
                        val telemetry = currentTelemetry.copy(dtcCount = dtcCount)
                        val published = mqttClient.publishTelemetry(telemetry)
                        if (published) {
                            _publishedMessages.value++
                            Log.v(TAG, "📡 Publicado #${_publishedMessages.value}")
                        }
                    } catch (e: Exception) {
                        // Error puntual — no interrumpir el loop
                        Log.w(TAG, "Error publicando (se reintentará): ${e.message}")
                        _state.value = TelemetryState.Error("Publish error: ${e.message}")
                        delay(1_000) // pequeña pausa antes de reintentar
                        _state.value = TelemetryState.Publishing
                    }

                    delay(config.publishIntervalMs)
                }

            } catch (e: Exception) {
                val msg = "Error fatal en telemetría: ${e.message}"
                Log.e(TAG, msg, e)
                _state.value = TelemetryState.Error(msg)
            }
        }
    }

    /**
     * Detiene el loop de publicación y desconecta del broker.
     */
    fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
        _state.value = TelemetryState.Idle
        Log.i(TAG, "🔴 Telemetría detenida")

        // Desconectar limpiamente en background
        scope.launch {
            try {
                mqttClient.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error al desconectar MQTT: ${e.message}")
            }
        }
    }

    /**
     * Prueba la conexión al broker con la URL indicada.
     * No altera el estado de publicación.
     *
     * @return true si la conexión fue exitosa
     */
    suspend fun testConnection(brokerUrl: String): Boolean {
        return try {
            val testClientId = "obelus_test_" + System.currentTimeMillis()
            val result = mqttClient.connect(brokerUrl, testClientId)
            if (result) {
                mqttClient.disconnect()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Prueba de conexión fallida: ${e.message}")
            false
        }
    }

    val isPublishing: Boolean get() = _state.value is TelemetryState.Publishing
}
