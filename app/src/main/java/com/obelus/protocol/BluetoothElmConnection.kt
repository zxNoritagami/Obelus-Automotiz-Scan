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

/**
 * Handles Bluetooth connection with ELM327 OBD2 adapter.
 * Uses RFCOMM socket (SPP) for communication.
 */
class BluetoothElmConnection @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?
) : ElmConnection {

    companion object {
        private const val TAG = "BluetoothElmConnection"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECTION_TIMEOUT_MS = 10000L
        private const val READ_TIMEOUT_MS = 2000L
        private const val BUFFER_SIZE = 1024
        private const val READ_DELAY_MS = 10L
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or disabled")
            _connectionState.value = ConnectionState.ERROR
            return@withContext false
        }

        _connectionState.value = ConnectionState.CONNECTING

        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            // Create socket
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            
            // Connect with timeout
            withTimeout(CONNECTION_TIMEOUT_MS) {
                socket?.connect()
            }

            // Get streams
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
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        } finally {
            socket = null
            inputStream = null
            outputStream = null
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.i(TAG, "Disconnected")
        }
    }

    override suspend fun send(command: String): String = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            throw IOException("Not connected")
        }

        try {
            // Ensure command ends with \r
            val cmdToSend = if (command.endsWith("\r")) command else "$command\r" 

            // Write command
            outputStream?.write(cmdToSend.toByteArray())
            outputStream?.flush()

            // Read response
            val response = readResponse()
            return@withContext response

        } catch (e: Exception) {
            Log.e(TAG, "Error sending command: $command", e)
            _connectionState.value = ConnectionState.ERROR
            throw e
        }
    }

    private suspend fun readResponse(): String {
        return withTimeout(READ_TIMEOUT_MS) {
            val buffer = ByteArray(BUFFER_SIZE)
            val sb = StringBuilder()
            var bytesRead: Int
            
            // Simple reading loop checking for prompt '>' character which usually indicates end of ELM response
            try {
                while (true) {
                    val stream = inputStream ?: throw IOException("Input stream null")
                    
                    if (stream.available() > 0) {
                        bytesRead = stream.read(buffer)
                        if (bytesRead > 0) {
                            val chunk = String(buffer, 0, bytesRead)
                            sb.append(chunk)
                            if (chunk.contains(">")) {
                                break // End of response
                            }
                        }
                    } else {
                         // Small delay to prevent CPU spinning while waiting for data
                         kotlinx.coroutines.delay(READ_DELAY_MS)
                    }
                }
            } catch (e: IOException) {
                 Log.e(TAG, "Read error", e)
                 throw e
            }
            
            // Remove the prompt character '>' and trim
            sb.toString().replace(">", "").trim()
        }
    }

    override fun isConnected(): Boolean {
        return socket?.isConnected == true
    }
}
