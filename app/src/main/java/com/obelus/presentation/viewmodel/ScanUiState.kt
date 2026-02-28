package com.obelus.presentation.viewmodel

import com.obelus.bluetooth.ConnectionState
import com.obelus.data.local.entity.DatabaseFile
import com.obelus.data.local.entity.DtcCode
import com.obelus.data.local.entity.SignalReading
import com.obelus.data.local.model.SignalStats
import com.obelus.protocol.OBD2Protocol

// ─────────────────────────────────────────────────────────────────────────────
// ScanUiState.kt
// Snapshot inmutable del estado de la UI de escaneo OBD2.
// Emitido como StateFlow desde ScanViewModel.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Estado completo de la pantalla de escaneo OBD2.
 *
 * Diseñado para ser inmutable: cada cambio produce una nueva copia
 * mediante [copy], garantizando la coherencia en los colectores de StateFlow.
 *
 * @param connectionState   Estado actual de la conexión Bluetooth / adaptador.
 * @param detectedProtocol  Protocolo OBD2 detectado (null hasta la detección).
 * @param isScanning        `true` si el loop de lectura continua está activo.
 * @param readings          Lecturas de señales de la sesión actual.
 * @param dtcs              Códigos de falla leídos del vehículo.
 * @param selectedDatabase  Archivo DBC / base de datos activo en memoria.
 * @param errorMessage      Mensaje de error para mostrarlo en la UI (null = sin error).
 * @param stats             Mapa nombre-señal → estadísticas agregadas (min/max/avg).
 * @param protocolSuggestion Sugerencia al usuario cuando la auto-detección falla.
 * @param sessionId         ID de la sesión activa en Room (null si no hay sesión).
 * @param dbcSignalCount    Número de señales cargadas del DBC activo.
 */
data class ScanUiState(
    val connectionState: ConnectionState       = ConnectionState.Disconnected,
    val detectedProtocol: OBD2Protocol?        = null,
    val isScanning: Boolean                    = false,
    val readings: List<SignalReading>          = emptyList(),
    val dtcs: List<DtcCode>                    = emptyList(),
    val selectedDatabase: DatabaseFile?        = null,
    val errorMessage: String?                  = null,
    val stats: Map<String, SignalStats>        = emptyMap(),
    val protocolSuggestion: String?            = null,
    val sessionId: Long?                       = null,
    val dbcSignalCount: Int                    = 0
) {
    // ── Derived helpers ────────────────────────────────────────────────────────

    /** `true` cuando hay una conexión BT activa pero NO estamos escaneando. */
    val isConnectedIdle: Boolean
        get() = connectionState is ConnectionState.Connected && !isScanning

    /** `true` si la conexión está en proceso (Connecting o Reconnecting). */
    val isConnecting: Boolean
        get() = connectionState is ConnectionState.Connecting ||
                connectionState is ConnectionState.Reconnecting

    /** Nombre amigable del adaptador conectado, o null. */
    val connectedDeviceName: String?
        get() = (connectionState as? ConnectionState.Connected)?.deviceName

    /** Número de DTCs activos (no pendientes). */
    val activeDtcCount: Int
        get() = dtcs.count { it.isActive }
}
