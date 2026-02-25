package com.obelus.data.protocol.wifi

import android.util.Log
import com.obelus.protocol.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

class ConnectionError(message: String, cause: Throwable? = null) : IOException(message, cause)
class TimeoutError(message: String) : IOException(message)

@Singleton
class Elm327WifiConnection @Inject constructor() : WifiConnection {

    companion object {
        private const val TAG = "Elm327WifiConnection"
        const val DEFAULT_IP = "192.168.0.10"
        const val DEFAULT_PORT = 35000
        private const val CONN_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 2000
        private val BACKOFF_DELAYS = listOf(1000L, 2000L, 4000L)
        private const val READ_BUFFER_SIZE = 1024
        private const val WRITE_BUFFER_SIZE = 64
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val sendMutex = Mutex()

    override suspend fun connect(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        if (_connectionState.value == ConnectionState.CONNECTING || _connectionState.value == ConnectionState.CONNECTED) {
            return@withContext true
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        
        var connected = false
        val targetIp = ip.ifBlank { DEFAULT_IP }
        val targetPort = if (port <= 0) DEFAULT_PORT else port

        for ((attempt, delayMs) in BACKOFF_DELAYS.withIndex()) {
            try {
                Log.d(TAG, "Attempt ${attempt + 1}/${BACKOFF_DELAYS.size} to connect to $targetIp:$targetPort")
                socket = Socket()
                socket?.connect(InetSocketAddress(targetIp, targetPort), CONN_TIMEOUT_MS)
                socket?.soTimeout = READ_TIMEOUT_MS
                
                inputStream = socket?.getInputStream()
                outputStream = socket?.getOutputStream()

                _connectionState.value = ConnectionState.CONNECTED
                connected = true
                Log.i(TAG, "Successfully connected to $targetIp:$targetPort")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Connection attempt ${attempt + 1} failed: ${e.message}")
                cleanup()
                if (attempt < BACKOFF_DELAYS.size - 1) {
                    delay(delayMs)
                }
            }
        }

        if (!connected) {
            _connectionState.value = ConnectionState.ERROR
            throw ConnectionError("Failed to connect after ${BACKOFF_DELAYS.size} attempts")
        }
        return@withContext connected
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        cleanup()
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "Disconnected")
    }

    private fun cleanup() {
        try { inputStream?.close() } catch (e: Exception) {}
        try { outputStream?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
    }

    override suspend fun sendCommand(cmd: String): String = withContext(Dispatchers.IO) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            throw ConnectionError("Cannot send command: Not connected")
        }

        sendMutex.withLock {
            return@withContext try {
                val commandToSend = "$cmd\r"
                val bytes = commandToSend.toByteArray()
                
                // Write buffer size limited to 64 bytes
                for (i in bytes.indices step WRITE_BUFFER_SIZE) {
                    val end = minOf(i + WRITE_BUFFER_SIZE, bytes.size)
                    outputStream?.write(bytes, i, end - i)
                }
                outputStream?.flush()

                readResponse()
            } catch (e: SocketTimeoutException) {
                _connectionState.value = ConnectionState.ERROR
                cleanup()
                throw TimeoutError("Timeout reading from socket")
            } catch (e: IOException) {
                _connectionState.value = ConnectionState.ERROR
                cleanup()
                throw ConnectionError("Error writing/reading from socket", e)
            }
        }
    }

    private fun readResponse(): String {
        val stream = inputStream ?: throw IOException("InputStream is null")
        val buffer = ByteArray(READ_BUFFER_SIZE)
        val responseList = mutableListOf<Byte>()

        while (true) {
            val bytesRead = stream.read(buffer)
            if (bytesRead == -1) {
                throw IOException("End of stream reached")
            }
            
            for (i in 0 until bytesRead) {
                val byte = buffer[i]
                responseList.add(byte)
                if (byte.toInt().toChar() == '>') {
                    return String(responseList.toByteArray()).replace(">", "").trim()
                }
            }
        }
    }

    override fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.CONNECTED && socket?.isConnected == true && !socket!!.isClosed
    }
}
