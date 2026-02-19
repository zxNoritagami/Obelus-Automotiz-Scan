package com.obelus.mqtt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estado de la conexión MQTT.
 */
enum class MqttConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * Datos de telemetría OBD2 para publicar por MQTT.
 */
data class ObdTelemetry(
    val rpm: Float = 0f,
    val speed: Float = 0f,
    val coolantTemp: Float = 0f,
    val engineLoad: Float = 0f,
    val throttlePos: Float = 0f,
    val mafRate: Float = 0f,
    val fuelLevel: Float = 0f,
    val dtcCount: Int = 0
)

/**
 * Cliente MQTT para publicar telemetría OBD2 en tiempo real.
 *
 * - Broker por defecto: HiveMQ público (broker.hivemq.com:1883)
 * - Tópico por defecto: obelus/{clientId}/telemetry
 * - Formato: JSON con timestamp ISO8601
 * - Reconexión automática con back-off exponencial
 *
 * Ejemplo de mensaje publicado:
 * ```json
 * {
 *   "ts": "2024-01-15T10:30:00Z",
 *   "rpm": 2500,
 *   "speed": 80,
 *   "coolant_temp": 90,
 *   "engine_load": 45.2,
 *   "throttle_pos": 30.1,
 *   "maf_rate": 12.5,
 *   "fuel_level": 65.0,
 *   "dtc_count": 0
 * }
 * ```
 */
@Singleton
class ObdMqttClient @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ObdMqttClient"

        // Broker público de HiveMQ (sin autenticación, solo para pruebas)
        const val DEFAULT_BROKER_URL = "tcp://broker.hivemq.com:1883"

        // Tópico base: obelus/<clientId>/telemetry
        const val TOPIC_TELEMETRY = "obelus/%s/telemetry"
        const val TOPIC_DTC       = "obelus/%s/dtc"
        const val TOPIC_STATUS    = "obelus/%s/status"

        // QoS 0 = at most once (mejor para telemetría de alta frecuencia)
        const val QOS_TELEMETRY = 0
        // QoS 1 = at least once (para DTCs y alertas, no se pueden perder)
        const val QOS_DTC       = 1

        // Timeouts y reintentos
        private const val CONNECTION_TIMEOUT_S = 10
        private const val KEEPALIVE_S          = 30
        private const val MAX_RECONNECT_DELAY_MS = 60_000L  // 60s máximo
        private const val INITIAL_RECONNECT_DELAY_MS = 2_000L

        // Formato ISO8601 UTC
        private val ISO8601_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
    }

    // ── Estado observable ─────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _publishedCount = MutableStateFlow(0L)
    val publishedCount: StateFlow<Long> = _publishedCount.asStateFlow()

    // ── Internals ─────────────────────────────────────────────────────────────

    private var mqttClient: MqttClient? = null
    private var brokerUrl: String = DEFAULT_BROKER_URL
    private var clientId: String = "obelus_" + System.currentTimeMillis()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var reconnectJob: Job? = null

    // ── Tópicos calculados ────────────────────────────────────────────────────

    val topicTelemetry get() = TOPIC_TELEMETRY.format(clientId)
    val topicDtc       get() = TOPIC_DTC.format(clientId)
    val topicStatus    get() = TOPIC_STATUS.format(clientId)

    // =========================================================================
    // CONEXIÓN
    // =========================================================================

    /**
     * Conecta al broker MQTT.
     *
     * @param brokerUrl URL del broker (default: HiveMQ público)
     * @param clientId  ID único del cliente (default: obelus_<timestamp>)
     */
    suspend fun connect(
        brokerUrl: String = DEFAULT_BROKER_URL,
        clientId: String  = "obelus_" + System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        this@ObdMqttClient.brokerUrl = brokerUrl
        this@ObdMqttClient.clientId  = clientId

        Log.i(TAG, "Conectando a $brokerUrl con clientId=$clientId")
        _connectionState.value = MqttConnectionState.CONNECTING

        return@withContext try {
            // Crear cliente con persistencia en memoria (no necesitamos disco)
            mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
            mqttClient!!.setCallback(mqttCallback)

            val options = buildConnectOptions()
            mqttClient!!.connect(options)

            _connectionState.value = MqttConnectionState.CONNECTED
            _lastError.value = null
            Log.i(TAG, "✅ Conectado a $brokerUrl")

            // Publicar estado online
            publishStatus("online")
            true

        } catch (e: MqttException) {
            val msg = "Error al conectar: ${e.reasonCode} - ${e.message}"
            Log.e(TAG, msg, e)
            _lastError.value = msg
            _connectionState.value = MqttConnectionState.ERROR
            false
        } catch (e: Exception) {
            val msg = "Error inesperado al conectar: ${e.message}"
            Log.e(TAG, msg, e)
            _lastError.value = msg
            _connectionState.value = MqttConnectionState.ERROR
            false
        }
    }

    /**
     * Desconecta limpiamente del broker MQTT.
     * Publica un mensaje "offline" antes de salir.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        reconnectJob?.cancel()
        reconnectJob = null

        try {
            if (mqttClient?.isConnected == true) {
                publishStatus("offline")
                mqttClient?.disconnect(1_000) // timeout 1s
                Log.i(TAG, "✅ Desconectado limpiamente")
            }
        } catch (e: MqttException) {
            Log.w(TAG, "Advertencia al desconectar: ${e.message}")
        } finally {
            try { mqttClient?.close() } catch (_: Exception) {}
            mqttClient = null
            _connectionState.value = MqttConnectionState.DISCONNECTED
        }
    }

    // =========================================================================
    // PUBLICACIÓN
    // =========================================================================

    /**
     * Publica telemetría OBD2 en tiempo real.
     * Genera automáticamente el timestamp ISO8601 UTC.
     *
     * @param telemetry Datos OBD2 actuales
     * @return true si se publicó correctamente
     */
    suspend fun publishTelemetry(telemetry: ObdTelemetry): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            Log.w(TAG, "No conectado. Descartando telemetría.")
            return@withContext false
        }

        val ts  = ISO8601_FORMATTER.format(Instant.now())
        val json = buildTelemetryJson(ts, telemetry)

        return@withContext try {
            publish(topicTelemetry, json, QOS_TELEMETRY)
            _publishedCount.value++
            Log.v(TAG, "📡 Publicado en $topicTelemetry → $json")
            true
        } catch (e: MqttException) {
            Log.e(TAG, "Error al publicar telemetría: ${e.message}", e)
            _lastError.value = "Publish error: ${e.message}"
            false
        }
    }

    /**
     * Publica una lista de DTCs activos.
     * QoS 1 para garantizar entrega (no se deben perder alertas de error).
     *
     * @param codes Lista de códigos DTC (ej: ["P0300", "P0420"])
     */
    suspend fun publishDtcs(codes: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext false

        val ts  = ISO8601_FORMATTER.format(Instant.now())
        val codesJson = codes.joinToString(",") { "\"$it\"" }
        val json = """{"ts":"$ts","count":${codes.size},"codes":[$codesJson]}"""

        return@withContext try {
            publish(topicDtc, json, QOS_DTC)
            Log.i(TAG, "🔴 DTCs publicados: $codes")
            true
        } catch (e: MqttException) {
            Log.e(TAG, "Error al publicar DTCs: ${e.message}", e)
            false
        }
    }

    // =========================================================================
    // RECONEXIÓN AUTOMÁTICA
    // =========================================================================

    /**
     * Inicia el loop de reconexión con back-off exponencial.
     * Reintenta cada vez con el doble de espera hasta [MAX_RECONNECT_DELAY_MS].
     */
    private fun startReconnectLoop() {
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            var delayMs = INITIAL_RECONNECT_DELAY_MS
            _connectionState.value = MqttConnectionState.RECONNECTING

            while (isActive && !isConnected()) {
                Log.i(TAG, "🔄 Reintentando conexión en ${delayMs / 1000}s...")
                delay(delayMs)

                val success = connect(brokerUrl, clientId)
                if (success) {
                    Log.i(TAG, "✅ Reconexión exitosa")
                    reconnectJob = null
                    return@launch
                }

                // Back-off exponencial con techo en MAX_RECONNECT_DELAY_MS
                delayMs = minOf(delayMs * 2, MAX_RECONNECT_DELAY_MS)
            }
        }
    }

    // =========================================================================
    // HELPERS INTERNOS
    // =========================================================================

    fun isConnected(): Boolean = mqttClient?.isConnected == true

    private fun buildConnectOptions() = MqttConnectOptions().apply {
        isCleanSession   = true
        connectionTimeout = CONNECTION_TIMEOUT_S
        keepAliveInterval = KEEPALIVE_S
        isAutomaticReconnect = false  // Lo manejamos nosotros con back-off

        // Will message: se envía automáticamente si el cliente se desconecta inesperadamente
        val willTs  = ISO8601_FORMATTER.format(Instant.now())
        val willMsg = """{"ts":"$willTs","status":"unexpected_disconnect"}""".toByteArray()
        setWill(topicStatus, willMsg, QOS_DTC, false)
    }

    private fun publish(topic: String, json: String, qos: Int) {
        val msg = MqttMessage(json.toByteArray(Charsets.UTF_8)).apply {
            this.qos      = qos
            this.isRetained = false
        }
        mqttClient?.publish(topic, msg)
    }

    private fun publishStatus(status: String) {
        try {
            val ts   = ISO8601_FORMATTER.format(Instant.now())
            val json = """{"ts":"$ts","status":"$status","client":"$clientId"}"""
            publish(topicStatus, json, QOS_DTC)
            Log.d(TAG, "Estado publicado: $status")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo publicar estado: ${e.message}")
        }
    }

    /**
     * Construye el JSON de telemetría con el formato requerido.
     *
     * Resultado: {"ts":"…","rpm":2500,"speed":80,"coolant_temp":90,"dtc_count":0,…}
     */
    private fun buildTelemetryJson(ts: String, t: ObdTelemetry): String {
        return """{"ts":"$ts","rpm":${t.rpm.toInt()},"speed":${t.speed.toInt()},"coolant_temp":${t.coolantTemp.toInt()},"engine_load":${"%.1f".format(t.engineLoad)},"throttle_pos":${"%.1f".format(t.throttlePos)},"maf_rate":${"%.2f".format(t.mafRate)},"fuel_level":${"%.1f".format(t.fuelLevel)},"dtc_count":${t.dtcCount}}"""
    }

    // =========================================================================
    // CALLBACK MQTT
    // =========================================================================

    private val mqttCallback = object : MqttCallback {

        override fun connectionLost(cause: Throwable?) {
            val msg = cause?.message ?: "Causa desconocida"
            Log.w(TAG, "⚠️ Conexión perdida: $msg")
            _connectionState.value = MqttConnectionState.RECONNECTING
            _lastError.value = "Conexión perdida: $msg"

            // Iniciar reconexión automática con back-off expon.
            startReconnectLoop()
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            // Este cliente solo publica, no se subscribe a nada por ahora
            Log.d(TAG, "Mensaje recibido en $topic: ${message?.toString()}")
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {
            Log.v(TAG, "✔ Entrega confirmada: ${token?.messageId}")
        }
    }
}
