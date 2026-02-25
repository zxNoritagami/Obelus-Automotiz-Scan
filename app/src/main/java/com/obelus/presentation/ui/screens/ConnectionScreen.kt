package com.obelus.presentation.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obelus.presentation.viewmodel.ConnectionViewModel
import com.obelus.presentation.viewmodel.UsbDeviceState
import com.obelus.protocol.ConnectionState

@Composable
fun ConnectionScreen(
    onNavigateToDashboard: () -> Unit = {},
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh device list whenever screen becomes visible
    LaunchedEffect(Unit) { viewModel.scanForUsbDevice() }

    // Auto-navigate when either connection becomes active
    LaunchedEffect(uiState.usbState, uiState.bluetoothState) {
        if (uiState.usbState == UsbDeviceState.CONNECTED ||
            uiState.bluetoothState == ConnectionState.CONNECTED) {
            onNavigateToDashboard()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        // ── Title ─────────────────────────────────────────────────────────────
        Text(
            text = "Conectar Adaptador OBD2",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Selecciona el tipo de conexión con tu adaptador ELM327",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Bluetooth Card ────────────────────────────────────────────────────
        ConnectionCard(
            title = "Bluetooth",
            subtitle = when (uiState.bluetoothState) {
                ConnectionState.CONNECTED    -> "Conectado: ${uiState.connectedDeviceName ?: "ELM327"}"
                ConnectionState.CONNECTING   -> "Conectando..."
                ConnectionState.ERROR        -> "Error de conexión"
                ConnectionState.DISCONNECTED -> "Buscar adaptadores BT cercanos"
            },
            icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White) },
            buttonLabel = when (uiState.bluetoothState) {
                ConnectionState.CONNECTED -> "Desconectar"
                ConnectionState.CONNECTING -> "Conectando..."
                else -> "Buscar Bluetooth"
            },
            isLoading = uiState.bluetoothState == ConnectionState.CONNECTING,
            isConnected = uiState.bluetoothState == ConnectionState.CONNECTED,
            cardColor = MaterialTheme.colorScheme.primaryContainer,
            onButtonClick = { /* BT handled by existing BluetoothViewModel */ }
        )

        // ── USB Card ──────────────────────────────────────────────────────────
        ConnectionCard(
            title = "USB OTG",
            subtitle = when (uiState.usbState) {
                UsbDeviceState.CONNECTED          -> "Conectado: ${uiState.usbDeviceName ?: "ELM327 USB"}"
                UsbDeviceState.CONNECTING         -> "Abriendo puerto USB..."
                UsbDeviceState.PERMISSION_REQUIRED-> "Toca para autorizar acceso USB"
                UsbDeviceState.DEVICE_FOUND       -> "ELM327 detectado: ${uiState.usbDeviceName}"
                UsbDeviceState.ERROR              -> uiState.errorMessage ?: "Error de conexión USB"
                UsbDeviceState.DISCONNECTED       -> "Conecta tu adaptador ELM327 USB vía OTG"
            },
            icon = {
                Icon(
                    if (uiState.usbState == UsbDeviceState.DISCONNECTED)
                        Icons.Default.UsbOff else Icons.Default.Usb,
                    contentDescription = null,
                    tint = Color.White
                )
            },
            buttonLabel = when (uiState.usbState) {
                UsbDeviceState.CONNECTED          -> "Desconectar USB"
                UsbDeviceState.CONNECTING         -> "Conectando..."
                UsbDeviceState.PERMISSION_REQUIRED-> "Autorizar USB"
                UsbDeviceState.DEVICE_FOUND       -> "Conectar vía USB"
                UsbDeviceState.ERROR              -> "Reintentar"
                UsbDeviceState.DISCONNECTED       -> "Buscar dispositivo USB"
            },
            isLoading = uiState.usbState == UsbDeviceState.CONNECTING,
            isConnected = uiState.usbState == UsbDeviceState.CONNECTED,
            cardColor = MaterialTheme.colorScheme.secondaryContainer,
            onButtonClick = {
                when (uiState.usbState) {
                    UsbDeviceState.CONNECTED          -> viewModel.disconnectUsb()
                    UsbDeviceState.PERMISSION_REQUIRED -> viewModel.requestUsbPermission()
                    UsbDeviceState.DISCONNECTED,
                    UsbDeviceState.ERROR              -> { viewModel.scanForUsbDevice(); viewModel.connectUsb() }
                    UsbDeviceState.DEVICE_FOUND       -> viewModel.requestUsbPermission()
                    else -> Unit
                }
            }
        )

        // ── Error message ─────────────────────────────────────────────────────
        if (uiState.usbState == UsbDeviceState.DISCONNECTED) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "💡 Para usar USB OTG necesitas un cable adaptador micro-USB/USB-C → USB-A (OTG) y el adaptador ELM327 con conector USB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── WiFi Card ─────────────────────────────────────────────────────────
        val wifiState by viewModel.wifiState.collectAsStateWithLifecycle()
        val wifiDevices by viewModel.wifiDevices.collectAsStateWithLifecycle()

        ConnectionCard(
            title = "WiFi TCP",
            subtitle = when (wifiState) {
                com.obelus.presentation.viewmodel.WifiConnectionState.CONNECTED -> "Conectado vía WiFi"
                com.obelus.presentation.viewmodel.WifiConnectionState.CONNECTING -> "Conectando al adaptador..."
                com.obelus.presentation.viewmodel.WifiConnectionState.SCANNING -> "Buscando adaptador en la red..."
                com.obelus.presentation.viewmodel.WifiConnectionState.ERROR -> "Error de red/timeout"
                com.obelus.presentation.viewmodel.WifiConnectionState.DISCONNECTED -> {
                    if (wifiDevices.isNotEmpty()) {
                        "Dispositivos encontrados: ${wifiDevices.size} (${wifiDevices.first().ip}:${wifiDevices.first().port})"
                    } else {
                        "Conéctate a la red WiFi del ELM327"
                    }
                }
            },
            icon = {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Bluetooth, // Use a placeholder since Wifi icon might not be imported
                    contentDescription = null,
                    tint = Color.White
                )
            },
            buttonLabel = when (wifiState) {
                com.obelus.presentation.viewmodel.WifiConnectionState.CONNECTED -> "Desconectar WiFi"
                com.obelus.presentation.viewmodel.WifiConnectionState.CONNECTING -> "Conectando..."
                com.obelus.presentation.viewmodel.WifiConnectionState.SCANNING -> "Escaneando red..."
                com.obelus.presentation.viewmodel.WifiConnectionState.ERROR -> "Reintentar"
                com.obelus.presentation.viewmodel.WifiConnectionState.DISCONNECTED -> {
                    if (wifiDevices.isNotEmpty()) "Conectar" else "Escanear red"
                }
            },
            isLoading = wifiState == com.obelus.presentation.viewmodel.WifiConnectionState.CONNECTING || wifiState == com.obelus.presentation.viewmodel.WifiConnectionState.SCANNING,
            isConnected = wifiState == com.obelus.presentation.viewmodel.WifiConnectionState.CONNECTED,
            cardColor = MaterialTheme.colorScheme.tertiaryContainer,
            onButtonClick = {
                when (wifiState) {
                    com.obelus.presentation.viewmodel.WifiConnectionState.CONNECTED -> viewModel.disconnectWifi()
                    com.obelus.presentation.viewmodel.WifiConnectionState.DISCONNECTED,
                    com.obelus.presentation.viewmodel.WifiConnectionState.ERROR -> {
                        if (wifiDevices.isNotEmpty()) {
                            val device = wifiDevices.first()
                            viewModel.connectWifi(device.ip, device.port)
                        } else {
                            viewModel.scanWifiDevices()
                        }
                    }
                    else -> Unit
                }
            }
        )
    }
}

@Composable
private fun ConnectionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    buttonLabel: String,
    isLoading: Boolean,
    isConnected: Boolean,
    cardColor: Color,
    onButtonClick: () -> Unit
) {
    val connectedColor = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        if (isConnected) connectedColor else Color.Transparent,
        animationSpec = tween(300), label = "border"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isConnected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon bubble
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) { icon() }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Button(
            onClick = onButtonClick,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(buttonLabel)
        }
    }
}
