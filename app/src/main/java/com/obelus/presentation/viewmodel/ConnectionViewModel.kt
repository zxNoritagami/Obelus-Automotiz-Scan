package com.obelus.presentation.viewmodel

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.protocol.ConnectionState
import com.obelus.protocol.usb.UsbElmConnection
import com.obelus.protocol.usb.UsbPermissionReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UsbDeviceState {
    DISCONNECTED,
    DEVICE_FOUND,      // adapter plugged in but permission not yet tested
    PERMISSION_REQUIRED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class WifiConnectionState {
    DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ERROR
}

data class ConnectionUiState(
    val bluetoothState: ConnectionState = ConnectionState.DISCONNECTED,
    val usbState: UsbDeviceState = UsbDeviceState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val usbDeviceName: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbElmConnection: UsbElmConnection,
    private val usbManager: UsbManager,
    private val obdRepository: com.obelus.data.repository.ObdRepository,
    private val wifiDiscovery: com.obelus.data.protocol.wifi.WifiDiscovery
) : ViewModel() {

    companion object { private const val TAG = "ConnectionViewModel" }

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    // ── WiFi ──────────────────────────────────────────────────────────────────
    private val _wifiDevices = MutableStateFlow<List<com.obelus.data.protocol.wifi.DiscoveredDevice>>(emptyList())
    val wifiDevices: StateFlow<List<com.obelus.data.protocol.wifi.DiscoveredDevice>> = _wifiDevices.asStateFlow()

    private val _wifiState = MutableStateFlow(WifiConnectionState.DISCONNECTED)
    val wifiState: StateFlow<WifiConnectionState> = _wifiState.asStateFlow()

    fun scanWifiDevices() {
        _wifiState.value = WifiConnectionState.SCANNING
        viewModelScope.launch {
            val devices = wifiDiscovery.scanNetwork()
            _wifiDevices.value = devices
            _wifiState.value = if (devices.isNotEmpty()) WifiConnectionState.DISCONNECTED else WifiConnectionState.ERROR
        }
    }

    fun connectWifi(ip: String, port: Int) {
        _wifiState.value = WifiConnectionState.CONNECTING
        viewModelScope.launch {
            obdRepository.setConnectionType(com.obelus.data.repository.ConnectionType.WIFI)
            val success = try {
                obdRepository.connectWifi(ip, port)
            } catch (e: Exception) {
                false
            }
            if (success) {
                _wifiState.value = WifiConnectionState.CONNECTED
            } else {
                _wifiState.value = WifiConnectionState.ERROR
            }
        }
    }

    fun disconnectWifi() {
        viewModelScope.launch {
            obdRepository.disconnect()
            _wifiState.value = WifiConnectionState.DISCONNECTED
        }
    }

    // Mirror the underlying connection state
    val usbConnectionState: StateFlow<ConnectionState> = usbElmConnection.connectionState
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.DISCONNECTED)

    init {
        observeUsbConnection()
        scanForUsbDevice()
        observeWifiConnection()
    }
    
    private fun observeWifiConnection() {
        viewModelScope.launch {
            obdRepository.connectionState.collect { state ->
                // Sólo actualizar si es wifi activo
                // Pero asumo actualiza basico
            }
        }
    }

    // ── USB ───────────────────────────────────────────────────────────────────

    /** Scan for already-attached ELM327 USB devices. */
    fun scanForUsbDevice() {
        val device = usbElmConnection.findElm327Device()
        if (device != null) {
            _uiState.value = _uiState.value.copy(
                usbState = if (usbManager.hasPermission(device))
                    UsbDeviceState.DEVICE_FOUND
                else
                    UsbDeviceState.PERMISSION_REQUIRED,
                usbDeviceName = device.productName ?: device.deviceName
            )
            Log.i(TAG, "ELM327 USB found: ${device.deviceName}")
        } else {
            _uiState.value = _uiState.value.copy(
                usbState = UsbDeviceState.DISCONNECTED,
                usbDeviceName = null
            )
        }
    }

    /**
     * Request USB permission via PendingIntent.
     * The result arrives in [UsbPermissionReceiver].
     */
    fun requestUsbPermission() {
        val device = usbElmConnection.findElm327Device() ?: run {
            _uiState.value = _uiState.value.copy(
                usbState = UsbDeviceState.ERROR,
                errorMessage = "Adaptador ELM327 USB no encontrado"
            )
            return
        }

        if (usbManager.hasPermission(device)) {
            connectUsb(device)
            return
        }

        val permIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(UsbPermissionReceiver.ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        usbManager.requestPermission(device, permIntent)
        _uiState.value = _uiState.value.copy(usbState = UsbDeviceState.PERMISSION_REQUIRED)
        Log.i(TAG, "USB permission requested for ${device.deviceName}")
    }

    /** Connect to the provided USB device (called after permission granted). */
    fun connectUsb(device: UsbDevice? = null) {
        _uiState.value = _uiState.value.copy(usbState = UsbDeviceState.CONNECTING)
        viewModelScope.launch {
            val ok = if (device != null)
                usbElmConnection.connectDevice(device)
            else
                usbElmConnection.connect("")

            if (!ok) {
                _uiState.value = _uiState.value.copy(
                    usbState = UsbDeviceState.ERROR,
                    errorMessage = "No se pudo conectar al adaptador USB"
                )
            }
            // usbConnectionState flow will emit CONNECTED / ERROR — observed in observeUsbConnection()
        }
    }

    /** Disconnect active USB connection. */
    fun disconnectUsb() {
        viewModelScope.launch {
            usbElmConnection.disconnect()
        }
    }

    private fun observeUsbConnection() {
        viewModelScope.launch {
            usbElmConnection.connectionState.collect { state ->
                val usbDeviceState = when (state) {
                    ConnectionState.CONNECTED    -> UsbDeviceState.CONNECTED
                    ConnectionState.CONNECTING   -> UsbDeviceState.CONNECTING
                    ConnectionState.DISCONNECTED -> UsbDeviceState.DISCONNECTED
                    ConnectionState.ERROR        -> UsbDeviceState.ERROR
                }
                _uiState.value = _uiState.value.copy(usbState = usbDeviceState)
            }
        }
    }
}
