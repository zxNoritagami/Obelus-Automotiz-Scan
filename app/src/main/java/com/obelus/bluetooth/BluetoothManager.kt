package com.obelus.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// BluetoothManager.kt
// Singleton Hilt que centraliza toda la lógica Bluetooth del proyecto.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gestiona el ciclo de vida completo de la conexión Bluetooth con el adaptador ELM327:
 * - Verificación de estado y permisos.
 * - Enumeración de dispositivos emparejados.
 * - Descubrimiento en tiempo real de nuevos dispositivos.
 * - Conexión RFCOMM con timeout configurable.
 * - Desconexión limpia y reconexión automática con backoff exponencial.
 *
 * @param bluetoothAdapter Adaptador BT del sistema (inyectado por Hilt).
 * @param context          ApplicationContext (inyectado por Hilt).
 */
@Singleton
class BluetoothManager @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val context: Context
) {
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECTION_TIMEOUT_MS = 10_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    // ── Estado observable ──────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── Estado interno ─────────────────────────────────────────────────────────

    private var activeConnection: BluetoothConnection? = null
    private var lastDevice: BluetoothDevice? = null
    private var managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val reconnectionManager = ReconnectionManager(
        onStateChange  = { state -> _connectionState.value = state },
        onReconnected  = { conn  -> activeConnection = conn },
        scope          = managerScope
    )

    // ── Permisos requeridos ────────────────────────────────────────────────────

    /**
     * Lista de permisos necesarios según el nivel de API del dispositivo.
     * Expuesta para que el ViewModel o la Activity la use con el launcher de Compose.
     */
    val requiredPermissions: List<String>
        get() = BluetoothPermissions.requiredPermissions

    // ── API pública ────────────────────────────────────────────────────────────

    /**
     * Comprueba si el adaptador Bluetooth está disponible y activado.
     */
    fun isBluetoothEnabled(): Boolean {
        val enabled = bluetoothAdapter?.isEnabled == true
        println("[BluetoothManager] isBluetoothEnabled → $enabled")
        return enabled
    }

    /**
     * Devuelve los dispositivos BT ya emparejados con el teléfono.
     * Requiere permiso BLUETOOTH_CONNECT (API 31+) o BLUETOOTH (legacy).
     *
     * @return Lista de [BluetoothDevice], vacía si no hay permisos o BT desactivado.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!isBluetoothEnabled()) return emptyList()
        return try {
            val devices = bluetoothAdapter!!.bondedDevices.toList()
            println("[BluetoothManager] getPairedDevices → ${devices.size} dispositivos")
            devices
        } catch (e: SecurityException) {
            println("[BluetoothManager] getPairedDevices – sin permisos: ${e.message}")
            emptyList()
        }
    }

    /**
     * Inicia el descubrimiento de dispositivos Bluetooth clásicos y emite cada
     * dispositivo encontrado como un elemento del Flow.
     *
     * El Flow se cancela automáticamente cuando el collector se cancela.
     * Requiere permisos BLUETOOTH_SCAN (API 31+) + ACCESS_FINE_LOCATION (legacy).
     *
     * @return [Flow] de [BluetoothDevice] descubiertos en tiempo real.
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(): Flow<BluetoothDevice> = callbackFlow {
        if (!isBluetoothEnabled()) {
            close(IllegalStateException("Bluetooth desactivado"))
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val name = safeDeviceName(it)
                        println("[BluetoothManager] Descubierto: $name (${it.address})")
                        trySend(it)
                    }
                } else if (intent?.action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED) {
                    println("[BluetoothManager] Descubrimiento finalizado.")
                    channel.close()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)

        try {
            bluetoothAdapter!!.cancelDiscovery()
            bluetoothAdapter.startDiscovery()
            println("[BluetoothManager] Descubrimiento iniciado.")
        } catch (e: SecurityException) {
            println("[BluetoothManager] startDiscovery – sin permisos: ${e.message}")
            close(e)
        }

        awaitClose {
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (_: SecurityException) {}
            context.unregisterReceiver(receiver)
            println("[BluetoothManager] Discovery flow cerrado.")
        }
    }

    /**
     * Conecta al [device] vía RFCOMM SPP con un timeout de 10 segundos.
     *
     * @param device Dispositivo BT a conectar.
     * @return [Result.success] con [BluetoothConnection] si conecta,
     *         [Result.failure] con la excepción si falla.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Result<BluetoothConnection> =
        withContext(Dispatchers.IO) {
            val name = safeDeviceName(device)
            println("[BluetoothManager] Conectando a \"$name\" (${device.address})…")
            _connectionState.value = ConnectionState.Connecting
            lastDevice = device

            try {
                bluetoothAdapter?.cancelDiscovery()

                val socket: BluetoothSocket = withTimeout(CONNECTION_TIMEOUT_MS) {
                    device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
                }

                val conn = BluetoothConnection(socket, name, device.address)
                activeConnection = conn
                _connectionState.value = ConnectionState.Connected(name, device.address)
                println("[BluetoothManager] ✓ Conectado a \"$name\".")
                Result.success(conn)

            } catch (e: IOException) {
                println("[BluetoothManager] ✗ IOException al conectar: ${e.message}")
                _connectionState.value = ConnectionState.Error("No se pudo conectar: ${e.message}")
                Result.failure(e)

            } catch (e: TimeoutCancellationException) {
                println("[BluetoothManager] ✗ Timeout (${CONNECTION_TIMEOUT_MS}ms) al conectar.")
                _connectionState.value = ConnectionState.Error("Timeout de conexión (${CONNECTION_TIMEOUT_MS / 1000}s)")
                Result.failure(IOException("Timeout de conexión"))

            } catch (e: SecurityException) {
                println("[BluetoothManager] ✗ Sin permisos para conectar: ${e.message}")
                _connectionState.value = ConnectionState.Error("Sin permisos Bluetooth")
                Result.failure(e)
            }
        }

    /**
     * Cierra la conexión activa de forma limpia y cancela la reconexión automática.
     */
    suspend fun disconnect() {
        println("[BluetoothManager] disconnect() solicitado.")
        reconnectionManager.cancel()
        activeConnection?.close()
        activeConnection = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Devuelve la [BluetoothConnection] activa, o `null` si no hay conexión.
     */
    fun getActiveConnection(): BluetoothConnection? = activeConnection

    /**
     * Inicia el proceso de reconexión automática con el último dispositivo conocido.
     * Se llama internamente cuando se detecta una desconexión inesperada.
     *
     * @param attempts Número máximo de intentos (default [MAX_RECONNECT_ATTEMPTS]).
     */
    fun scheduleReconnection(attempts: Int = MAX_RECONNECT_ATTEMPTS) {
        val device = lastDevice ?: run {
            println("[BluetoothManager] scheduleReconnection – sin dispositivo previo.")
            _connectionState.value = ConnectionState.Error("Sin dispositivo para reconectar")
            return
        }
        reconnectionManager.scheduleReconnection(device, attempts) { dev ->
            connect(dev).getOrNull()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String =
        try { device.name ?: device.address } catch (_: SecurityException) { device.address ?: "?" }
}
