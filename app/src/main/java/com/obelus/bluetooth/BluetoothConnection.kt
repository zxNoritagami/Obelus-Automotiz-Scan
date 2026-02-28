package com.obelus.bluetooth

import android.bluetooth.BluetoothSocket
import com.obelus.protocol.ElmConnection
import com.obelus.protocol.ConnectionState as ProtocolConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// ─────────────────────────────────────────────────────────────────────────────
// BluetoothConnection.kt
// Wrapper sobre BluetoothSocket que implementa ElmConnection.
// Proporciona I/O orientado a líneas con timeout y manejo robusto de errores.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Envuelve un [BluetoothSocket] activo y expone operaciones de I/O de alto nivel
 * orientadas a strings, necesarias para comunicarse con el ELM327.
 *
 * Implementa [ElmConnection] para que sea directamente utilizable por
 * [com.obelus.protocol.ProtocolDetector] e [com.obelus.protocol.IsoTPHandler].
 *
 * @param socket Socket RFCOMM ya conectado.
 * @param deviceName Nombre amigable del adaptador para logs.
 * @param deviceAddress Dirección MAC del adaptador.
 */
class BluetoothConnection(
    private val socket: BluetoothSocket,
    val deviceName: String = "ELM327",
    val deviceAddress: String = ""
) : ElmConnection {

    companion object {
        private const val BUFFER_SIZE      = 2048
        private const val READ_POLL_MS     = 10L
        private const val DEFAULT_TIMEOUT  = 1000L   // ms para readLine
        private const val COMMAND_TIMEOUT  = 5000L   // ms para send/readResponse
        private const val PROMPT_CHAR      = '>'     // ELM327 envía '>' al terminar
    }

    // ── Estado de conexión (implementa ElmConnection) ─────────────────────────

    private val _connectionState = MutableStateFlow(ProtocolConnectionState.CONNECTED)
    override val connectionState: StateFlow<ProtocolConnectionState> = _connectionState.asStateFlow()

    // ── Streams ───────────────────────────────────────────────────────────────

    val inputStream: InputStream  = socket.inputStream
    val outputStream: OutputStream = socket.outputStream
    private val readBuffer = ByteArray(BUFFER_SIZE)

    // ── ElmConnection API ──────────────────────────────────────────────────────

    /** No aplica en esta implementación: el socket ya está conectado al construir. */
    override suspend fun connect(deviceAddress: String): Boolean {
        println("[BluetoothConnection] connect() llamado en socket ya activo → ignorado.")
        return isConnected()
    }

    /** Cierra streams y socket de forma limpia. */
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        close()
    }

    /**
     * Envía [command] al ELM327 y espera la respuesta completa (hasta el prompt '>').
     *
     * @param command Comando AT u OBD2 sin '\r' (se añade automáticamente).
     * @return Respuesta sin el prompt, o cadena de error si falla.
     */
    override suspend fun send(command: String): String = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            println("[BluetoothConnection] send() – socket desconectado.")
            return@withContext "BUS ERROR"
        }
        try {
            val raw = if (command.endsWith("\r")) command else "$command\r"
            outputStream.write(raw.toByteArray(Charsets.US_ASCII))
            outputStream.flush()
            println("[BluetoothConnection] TX → \"${command.trimEnd()}\"")
            readResponse()
        } catch (e: IOException) {
            println("[BluetoothConnection] Error en send(): ${e.message}")
            _connectionState.value = ProtocolConnectionState.ERROR
            "IO ERROR: ${e.message}"
        }
    }

    /** Reconecta usando la misma dirección MAC (delegado al manager externo). */
    override suspend fun reconnect() {
        println("[BluetoothConnection] reconnect() – debe ser gestionado por BluetoothManager.")
    }

    override fun isConnected(): Boolean =
        socket.isConnected && try { outputStream.let { true } } catch (_: Exception) { false }

    // ── I/O de alto nivel ──────────────────────────────────────────────────────

    /**
     * Envía [data] al adaptador agregando '\r' al final.
     *
     * @return `true` si se escribió correctamente.
     */
    fun send(data: String, addCarriageReturn: Boolean = true): Boolean {
        return try {
            val raw = if (addCarriageReturn && !data.endsWith("\r")) "$data\r" else data
            outputStream.write(raw.toByteArray(Charsets.US_ASCII))
            outputStream.flush()
            true
        } catch (e: IOException) {
            println("[BluetoothConnection] send(sync) error: ${e.message}")
            false
        }
    }

    /**
     * Lee una línea del stream esperando hasta [timeoutMs] ms.
     *
     * @param timeoutMs Tiempo máximo de espera en milisegundos.
     * @return La línea leída (sin '\r\n'), o `null` si se agotó el tiempo.
     */
    suspend fun readLine(timeoutMs: Long = DEFAULT_TIMEOUT): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                val sb = StringBuilder()
                while (isActive) {
                    if (inputStream.available() > 0) {
                        val b = inputStream.read()
                        if (b == -1) break
                        val c = b.toChar()
                        if (c == '\n' || c == '\r') {
                            val line = sb.toString().trim()
                            if (line.isNotEmpty()) return@withTimeoutOrNull line
                        } else {
                            sb.append(c)
                        }
                    } else {
                        delay(READ_POLL_MS)
                    }
                }
                null
            }
        }

    /**
     * Lee todo el contenido disponible en el buffer de entrada sin bloquear.
     *
     * @return Cadena con todo lo disponible actualmente, o vacío si no hay nada.
     */
    fun readAvailable(): String {
        return try {
            val available = inputStream.available()
            if (available <= 0) return ""
            val bytes = inputStream.read(readBuffer, 0, minOf(available, BUFFER_SIZE))
            if (bytes > 0) String(readBuffer, 0, bytes, Charsets.US_ASCII) else ""
        } catch (e: IOException) {
            println("[BluetoothConnection] readAvailable() error: ${e.message}")
            ""
        }
    }

    /**
     * Cierra los streams y el socket de forma segura.
     * Después de llamar a este método el objeto no es reutilizable.
     */
    fun close() {
        try { inputStream.close()  } catch (_: Exception) {}
        try { outputStream.close() } catch (_: Exception) {}
        try { socket.close()       } catch (_: Exception) {}
        _connectionState.value = ProtocolConnectionState.DISCONNECTED
        println("[BluetoothConnection] Socket cerrado ($deviceName).")
    }

    // ── Privado ───────────────────────────────────────────────────────────────

    private suspend fun readResponse(): String {
        return withTimeout(COMMAND_TIMEOUT) {
            val sb = StringBuilder()
            while (isActive) {
                if (inputStream.available() > 0) {
                    val bytesRead = inputStream.read(readBuffer)
                    if (bytesRead > 0) {
                        val chunk = String(readBuffer, 0, bytesRead, Charsets.US_ASCII)
                        sb.append(chunk)
                        if (chunk.contains(PROMPT_CHAR)) break
                    }
                } else {
                    delay(READ_POLL_MS)
                }
            }
            val response = sb.toString().replace(PROMPT_CHAR.toString(), "").trim()
            println("[BluetoothConnection] RX ← \"$response\"")
            response
        }
    }
}
