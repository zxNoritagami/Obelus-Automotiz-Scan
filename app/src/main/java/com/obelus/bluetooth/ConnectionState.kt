package com.obelus.bluetooth

// ─────────────────────────────────────────────────────────────────────────────
// ConnectionState.kt
// Modela todos los estados posibles de la conexión Bluetooth con el ELM327.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sealed class que representa cada estado posible de la conexión BT.
 *
 * Flujo típico:
 *   Disconnected → Connecting → Connected
 *                               ↓ (fallo físico)
 *                           Reconnecting(1/3) → Reconnecting(2/3) → ...
 *                               ↓ (éxito)        ↓ (agotados)
 *                           Connected           Error
 */
sealed class ConnectionState {

    /** Sin conexión activa. Estado inicial y final tras un disconnect() voluntario. */
    data object Disconnected : ConnectionState()

    /** Intentando establecer el socket RFCOMM. */
    data object Connecting : ConnectionState()

    /**
     * Conexión activa y funcional.
     * @param deviceName Nombre amigable del adaptador BT conectado.
     * @param deviceAddress Dirección MAC del adaptador.
     */
    data class Connected(
        val deviceName: String,
        val deviceAddress: String = ""
    ) : ConnectionState()

    /**
     * Reconectando automáticamente tras una desconexión inesperada.
     * @param attempt     Número de intento actual (base 1).
     * @param maxAttempts Total de intentos configurados.
     * @param deviceName  Nombre del dispositivo al que se intenta reconectar.
     */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
        val deviceName: String = ""
    ) : ConnectionState()

    /**
     * Error no recuperable o agotamiento de intentos de reconexión.
     * @param message Descripción legible del error.
     */
    data class Error(val message: String) : ConnectionState()

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun isActive(): Boolean    = this is Connected
    fun isIdle(): Boolean      = this is Disconnected
    fun isTransient(): Boolean = this is Connecting || this is Reconnecting
    fun hasError(): Boolean    = this is Error

    override fun toString(): String = when (this) {
        is Disconnected  -> "ConnectionState.Disconnected"
        is Connecting    -> "ConnectionState.Connecting"
        is Connected     -> "ConnectionState.Connected(device=\"$deviceName\")"
        is Reconnecting  -> "ConnectionState.Reconnecting($attempt/$maxAttempts device=\"$deviceName\")"
        is Error         -> "ConnectionState.Error(\"$message\")"
    }
}
