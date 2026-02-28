package com.obelus.protocol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.pow

/**
 * Handles Bluetooth connection with ELM327 OBD2 adapter.
 * Uses RFCOMM socket (SPP) for communication.
 * Enhanced with connection monitoring, auto-reconnect and robust IO.
 */
class BluetoothElmConnection @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?
) : ElmConnection {

    companion object {
        private const val TAG = "BluetoothElmConnection"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECTION_TIMEOUT_MS = 10000L
        private const val COMMAND_TIMEOUT_MS = 5000L
        private const val BUFFER_SIZE = 1024
        private const val READ_DELAY_MS = 10L
        private const val MAX_RETRIES = 2
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var connectionMonitorJob: Job? = null
    private var lastUsedAddress: String? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val readBuffer = ByteArray(BUFFER_SIZE)
    private var isReconnecting = false

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth no disponible o desactivado")
            _connectionState.value = ConnectionState.ERROR
            return@withContext false
        }

        lastUsedAddress = deviceAddress
        _connectionState.value = ConnectionState.CONNECTING

        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                ?: throw Exception("Dispositivo no encontrado")
            
            // Cancelar descubrimiento si está activo (mejora velocidad de conexión)
            bluetoothAdapter.cancelDiscovery()
            
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            
            withTimeout(CONNECTION_TIMEOUT_MS) {
                socket?.connect()
            }

            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            if (socket?.isConnected == true) {
                _connectionState.value = ConnectionState.CONNECTED
                isReconnecting = false
                startConnectionMonitor()
                Log.i(TAG, "Conectado a $deviceAddress")
                return@withContext true
            } else {
                throw IOException("Socket no conectado tras intento")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error de conexión: ${e.message}")
            cleanup()
            if (!isReconnecting) {
                _connectionState.value = ConnectionState.ERROR
            }
            return@withContext false
        }
    }

    /**
     * Inicia un hilo de monitoreo para detectar desconexiones físicas.
     */
    private fun startConnectionMonitor() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isConnected()) {
                delay(5000)
                // Un simple test de lectura/escritura silencioso si fuera necesario,
                // pero isConnected() suele ser suficiente si el stack de Android detecta el cierre.
            }
            if (isActive && !isReconnecting) {
                Log.w(TAG, "Desconexión detectada por el monitor")
                handleUnexpectedDisconnect()
            }
        }
    }

    private suspend fun handleUnexpectedDisconnect() {
        _connectionState.value = ConnectionState.ERROR
        val address = lastUsedAddress ?: return
        
        isReconnecting = true
        Log.i(TAG, "Iniciando intento de reconexión automática...")
        
        var attempts = 0
        while (attempts < 3 && !isConnected()) {
            attempts++
            Log.d(TAG, "Reintentando conexión ($attempts/3)...")
            if (connect(address)) {
                Log.i(TAG, "Reconexión exitosa")
                return
            }
            delay(RECONNECT_DELAY_MS * attempts)
        }
        
        isReconnecting = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun disconnect() {
        isReconnecting = false
        connectionMonitorJob?.cancel()
        cleanup()
    }

    private suspend fun cleanup() = withContext(Dispatchers.IO) {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar recursos", e)
        } finally {
            socket = null
            inputStream = null
            outputStream = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    override suspend fun reconnect() {
        lastUsedAddress?.let { connect(it) }
    }

    override suspend fun send(command: String): String = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext "BUS ERROR"
        }

        var lastError = ""
        for (attempt in 0..MAX_RETRIES) {
            try {
                val cmdToSend = if (command.endsWith("\r")) command else "$command\r"
                outputStream?.write(cmdToSend.toByteArray())
                outputStream?.flush()

                return@withContext readResponse()
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown"
                Log.w(TAG, "Error en envío (intento $attempt): $lastError")
                if (attempt == MAX_RETRIES) break
                delay(200)
            }
        }
        
        return@withContext "ERROR: $lastError"
    }

    private suspend fun readResponse(): String {
        return withTimeout(COMMAND_TIMEOUT_MS) {
            val sb = StringBuilder()
            while (isActive) {
                val stream = inputStream ?: throw IOException("Input stream null")
                if (stream.available() > 0) {
                    val bytesRead = stream.read(readBuffer)
                    if (bytesRead > 0) {
                        val chunk = String(readBuffer, 0, bytesRead)
                        sb.append(chunk)
                        if (chunk.contains(">")) break
                    }
                } else {
                    delay(READ_DELAY_MS)
                }
            }
            sb.toString().replace(">", "").trim()
        }
    }

    override fun isConnected(): Boolean {
        return socket != null && socket!!.isConnected
    }
}
