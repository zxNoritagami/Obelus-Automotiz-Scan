package com.obelus.protocol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.pow

/**
 * Handles Bluetooth connection with ELM327 OBD2 adapter.
 * Uses RFCOMM socket (SPP) for communication.
 * Enhanced with 5s timeout, retries and structured logging.
 */
class BluetoothElmConnection @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?
) : ElmConnection {

    companion object {
        private const val TAG = "BluetoothElmConnection"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECTION_TIMEOUT_MS = 10000L
        private const val COMMAND_TIMEOUT_MS = 5000L // 5s timeout per request
        private const val BUFFER_SIZE = 1024
        private const val READ_DELAY_MS = 10L
        private const val MAX_RETRIES = 2
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val readBuffer = ByteArray(BUFFER_SIZE)
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 3

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or disabled")
            _connectionState.value = ConnectionState.ERROR
            return@withContext false
        }

        _connectionState.value = ConnectionState.CONNECTING
        reconnectAttempts = 0

        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                ?: throw Exception("Device not found")
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            
            withTimeout(CONNECTION_TIMEOUT_MS) {
                socket?.connect()
            }

            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            if (socket?.isConnected == true) {
                _connectionState.value = ConnectionState.CONNECTED
                Log.i(TAG, "Connected to $deviceAddress")
                return@withContext true
            } else {
                Log.e(TAG, "Socket not connected after connect()")
                disconnectInternal()
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}", e)
            disconnectInternal()
            return@withContext false
        }
    }

    override suspend fun disconnect() {
        disconnectInternal()
    }

    private suspend fun disconnectInternal() = withContext(Dispatchers.IO) {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket or streams", e)
        } finally {
            socket = null
            inputStream = null
            outputStream = null
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.i(TAG, "Disconnected")
        }
    }

    override suspend fun reconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached ($MAX_RECONNECT_ATTEMPTS)")
            _connectionState.value = ConnectionState.ERROR
            return
        }
        reconnectAttempts++
        Log.w(TAG, "Retrying connection... (Attempt $reconnectAttempts)")
        
        val lastDevice = socket?.remoteDevice?.address
        disconnect()
        if (lastDevice != null) {
            kotlinx.coroutines.delay(2000)
            connect(lastDevice)
        }
    }

    override suspend fun send(command: String): String = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            Log.e(TAG, "Send failed: Not connected")
            return@withContext "ECU SILENT"
        }

        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                if (attempt > 0) {
                    val backoff = (2.0.pow(attempt).toLong() * 500)
                    Log.w(TAG, "Retry $attempt for command $command after ${backoff}ms")
                    kotlinx.coroutines.delay(backoff)
                }

                val cmdToSend = if (command.endsWith("\r")) command else "$command\r" 
                outputStream?.write(cmdToSend.toByteArray())
                outputStream?.flush()

                val response = readResponse()
                Log.d(TAG, "[$command] -> $response")
                return@withContext response

            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt $attempt failed for command $command: ${e.message}")
                if (e is IOException) {
                    _connectionState.value = ConnectionState.ERROR
                    break // Don't retry on physical IO failure here, wait for reconnect
                }
            }
        }

        Log.e(TAG, "Command $command failed after $MAX_RETRIES retries. Last error: ${lastException?.message}")
        return@withContext "ECU SILENT"
    }

    private suspend fun readResponse(): String {
        return withTimeout(COMMAND_TIMEOUT_MS) {
            val sb = StringBuilder()
            try {
                while (true) {
                    val stream = inputStream ?: throw IOException("Input stream null")
                    if (stream.available() > 0) {
                        val bytesRead = stream.read(readBuffer)
                        if (bytesRead > 0) {
                            val chunk = String(readBuffer, 0, bytesRead)
                            sb.append(chunk)
                            if (chunk.contains(">")) break
                        }
                    } else {
                         kotlinx.coroutines.delay(READ_DELAY_MS)
                    }
                }
            } catch (e: Exception) {
                 throw e
            }
            sb.toString().replace(">", "").trim()
        }
    }

    override fun isConnected(): Boolean {
        return socket?.isConnected == true
    }
}
