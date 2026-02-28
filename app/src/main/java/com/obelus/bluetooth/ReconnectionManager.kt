package com.obelus.bluetooth

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.*

// ─────────────────────────────────────────────────────────────────────────────
// ReconnectionManager.kt
// Gestiona la reconexión automática con backoff exponencial (1s → 2s → 4s).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maneja intentos de reconexión automática tras una desconexión inesperada.
 *
 * Características:
 * - Backoff exponencial: 1s, 2s, 4s … entre intentos.
 * - Cancelación limpia cuando el usuario llama a `cancel()`.
 * - Notifica el estado vía callback [onStateChange].
 * - Inyectable vía Hilt (sin Android framework directo, sólo Coroutines).
 *
 * @param onStateChange Callback invocado en cada cambio de estado.
 * @param onReconnected Callback invocado cuando la reconexión es exitosa;
 *                       recibe la nueva [BluetoothConnection].
 * @param scope          CoroutineScope externo (del caller) para el ciclo de vida.
 */
class ReconnectionManager(
    private val onStateChange: (ConnectionState) -> Unit,
    private val onReconnected: suspend (BluetoothConnection) -> Unit,
    private val scope: CoroutineScope
) {
    companion object {
        private const val BASE_DELAY_MS = 1000L
        private const val MAX_DELAY_MS  = 30000L
    }

    private var reconnectJob: Job? = null
    private var cancelled = false

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Programa intentos de reconexión al [device] dado.
     *
     * @param device      Dispositivo BT al que reconectar.
     * @param attempts    Número máximo de intentos (default 3).
     * @param connectFn   Función suspendida que intenta la conexión y devuelve
     *                    un [BluetoothConnection] o null si falló.
     */
    fun scheduleReconnection(
        device: BluetoothDevice,
        attempts: Int = 3,
        connectFn: suspend (BluetoothDevice) -> BluetoothConnection?
    ) {
        cancel() // cancelar cualquier intento previo
        cancelled = false
        val deviceName = safeDeviceName(device)

        println("[ReconnectionManager] Programando reconexión a \"$deviceName\" ($attempts intentos)…")

        reconnectJob = scope.launch(Dispatchers.IO) {
            for (attempt in 1..attempts) {
                if (cancelled || !isActive) {
                    println("[ReconnectionManager] Reconexión cancelada antes del intento $attempt.")
                    break
                }

                // ── Notificar estado Reconnecting ────────────────────────────
                val state = ConnectionState.Reconnecting(
                    attempt = attempt,
                    maxAttempts = attempts,
                    deviceName = deviceName
                )
                onStateChange(state)
                println("[ReconnectionManager] Intento $attempt/$attempts para \"$deviceName\"…")

                // ── Backoff exponencial ──────────────────────────────────────
                val delayMs = (BASE_DELAY_MS * Math.pow(2.0, (attempt - 1).toDouble()))
                    .toLong()
                    .coerceAtMost(MAX_DELAY_MS)
                println("[ReconnectionManager] Esperando ${delayMs}ms antes del intento…")
                delay(delayMs)

                if (cancelled || !isActive) break

                // ── Intentar conexión ────────────────────────────────────────
                val connection = try {
                    connectFn(device)
                } catch (e: Exception) {
                    println("[ReconnectionManager] Excepción en intento $attempt: ${e.message}")
                    null
                }

                if (connection != null) {
                    println("[ReconnectionManager] ✓ Reconexión exitosa en intento $attempt.")
                    onStateChange(ConnectionState.Connected(deviceName, safeDeviceAddress(device)))
                    onReconnected(connection)
                    return@launch
                } else {
                    println("[ReconnectionManager] ✗ Intento $attempt fallido.")
                }
            }

            // Agotamos todos los intentos
            if (!cancelled) {
                val msg = "No se pudo reconectar a \"$deviceName\" tras $attempts intentos."
                println("[ReconnectionManager] $msg")
                onStateChange(ConnectionState.Error(msg))
            }
        }
    }

    /**
     * Cancela cualquier intento de reconexión en curso.
     * Llamar antes de un disconnect() voluntario del usuario.
     */
    fun cancel() {
        cancelled = true
        reconnectJob?.cancel()
        reconnectJob = null
        println("[ReconnectionManager] Reconexión cancelada por el caller.")
    }

    /** Indica si hay un proceso de reconexión activo en este momento. */
    fun isReconnecting(): Boolean = reconnectJob?.isActive == true

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String =
        try { device.name ?: device.address } catch (_: SecurityException) { device.address ?: "?" }

    @Suppress("MissingPermission")
    private fun safeDeviceAddress(device: BluetoothDevice): String =
        try { device.address ?: "" } catch (_: SecurityException) { "" }
}
