package com.obelus.presentation.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obelus.bluetooth.BluetoothManager
import com.obelus.bluetooth.ConnectionState
import com.obelus.presentation.ui.components.*
import com.obelus.presentation.viewmodel.ScanViewModel

// ─────────────────────────────────────────────────────────────────────────────
// DeviceScanScreen.kt
// Pantalla de búsqueda y conexión de adaptadores Bluetooth ELM327.
// Material3 – dark-first – sin lógica de negocio en la UI.
// ─────────────────────────────────────────────────────────────────────────────

/** Permisos Bluetooth según versión de Android. */
private val BLUETOOTH_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
} else {
    arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
}

/**
 * Pantalla principal de descubrimiento y conexión Bluetooth.
 *
 * @param onNavigateToScan  Callback para navegar a [ScanScreen] tras conectar.
 * @param bluetoothManager  Manager de Bluetooth inyectado (o del ViewModel).
 * @param viewModel         [ScanViewModel] inyectado por Hilt.
 */
@Composable
fun DeviceScanScreen(
    onNavigateToScan: () -> Unit = {},
    bluetoothManager: BluetoothManager = hiltViewModel<ScanViewModel>()
        .let { return@let TODO("Use BluetoothManager directly via injection") },
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DeviceScanContent(
        connectionState  = uiState.connectionState,
        onConnectDevice  = { viewModel.connect(it) },
        onDisconnect     = { viewModel.disconnect() },
        onNavigateToScan = onNavigateToScan
    )
}

/**
 * Versión stateless del contenido de la pantalla (facilita previews y tests).
 *
 * @param connectionState       Estado de conexión BT actual.
 * @param pairedDevices         Lista de dispositivos emparejados (inyectada desde BluetoothManager).
 * @param discoveredDevices     Lista de dispositivos descubiertos durante el scan.
 * @param isDiscovering         `true` si el BT está en modo descubrimiento activo.
 * @param isBluetoothEnabled    Estado del adaptador BT del sistema.
 * @param connectingAddress     Dirección MAC del dispositivo al que se está conectando.
 * @param onConnectDevice       Callback al pulsar "Conectar" en un ítem.
 * @param onDisconnect          Callback para desconectar el dispositivo actual.
 * @param onStartDiscovery      Callback para iniciar búsqueda de nuevos dispositivos.
 * @param onEnableBluetooth     Callback para abrir el diálogo de activar BT.
 * @param onNavigateToScan      Navegar a la pantalla de escaneo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScanContent(
    connectionState: ConnectionState      = ConnectionState.Disconnected,
    pairedDevices: List<DeviceUiModel>    = emptyList(),
    discoveredDevices: List<DeviceUiModel> = emptyList(),
    isDiscovering: Boolean                = false,
    isBluetoothEnabled: Boolean           = true,
    connectingAddress: String?            = null,
    onConnectDevice: (BluetoothDevice) -> Unit = {},
    onDisconnect: () -> Unit              = {},
    onStartDiscovery: () -> Unit          = {},
    onEnableBluetooth: () -> Unit         = {},
    onNavigateToScan: () -> Unit          = {}
) {
    val context = LocalContext.current

    // ── Estado de permisos ────────────────────────────────────────────────────
    var permissionState by remember { mutableStateOf<PermissionState?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        permissionState = when {
            allGranted -> null
            else       -> PermissionState.DENIED
        }
        if (allGranted) onStartDiscovery()
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Resultado ignorado; BluetoothManager observará el cambio */ }

    // ── Auto-navegar cuando se conecta ────────────────────────────────────────
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) onNavigateToScan()
    }

    // ── Scaffolding ───────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Conectar Adaptador",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "ELM327 vía Bluetooth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    ConnectionStatusChip(
                        isConnected = connectionState is ConnectionState.Connected,
                        isScanning  = isDiscovering,
                        modifier    = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── Banner BT desactivado ─────────────────────────────────────────
            if (!isBluetoothEnabled) {
                item(key = "bt_off_banner") {
                    BtDisabledBanner(
                        onEnable = {
                            enableBtLauncher.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            )
                        }
                    )
                }
            }

            // ── Estado de conexión actual (conectando / error) ────────────────
            item(key = "connection_state") {
                AnimatedVisibility(
                    visible = connectionState !is ConnectionState.Disconnected &&
                              connectionState !is ConnectionState.Connected,
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    ConnectionStateBanner(state = connectionState, onCancel = onDisconnect)
                }
            }

            // ── Dispositivos emparejados ──────────────────────────────────────
            if (pairedDevices.isNotEmpty()) {
                item(key = "paired_header") {
                    SectionHeader(
                        title    = "Dispositivos emparejados",
                        subtitle = "${pairedDevices.size} dispositivo(s)",
                        icon     = Icons.Default.Bluetooth
                    )
                }
                items(pairedDevices, key = { "paired_${it.address}" }) { dev ->
                    val itemState = when {
                        connectionState is ConnectionState.Connected &&
                        (connectionState as ConnectionState.Connected).deviceAddress == dev.address ->
                            DeviceConnectionState.CONNECTED
                        connectingAddress == dev.address -> DeviceConnectionState.CONNECTING
                        else                             -> DeviceConnectionState.IDLE
                    }
                    DeviceItem(device = dev, state = itemState) { /* connect via address lookup */ }
                }
            }

            // ── Dispositivos descubiertos ─────────────────────────────────────
            item(key = "scan_controls") {
                ScanControlRow(
                    isDiscovering      = isDiscovering,
                    isBluetoothEnabled = isBluetoothEnabled,
                    onScan = {
                        permissionLauncher.launch(BLUETOOTH_PERMISSIONS)
                    }
                )
            }

            if (isDiscovering && discoveredDevices.isEmpty()) {
                item(key = "scanning_placeholder") { ScanningEmptyPlaceholder() }
            }

            if (discoveredDevices.isNotEmpty()) {
                item(key = "discovered_header") {
                    SectionHeader(
                        title    = "Dispositivos encontrados",
                        subtitle = if (isDiscovering) "Buscando…" else "${discoveredDevices.size} encontrado(s)",
                        icon     = Icons.Default.Search
                    )
                }
                items(discoveredDevices, key = { "disc_${it.address}" }) { dev ->
                    val itemState = when {
                        connectingAddress == dev.address -> DeviceConnectionState.CONNECTING
                        else                             -> DeviceConnectionState.IDLE
                    }
                    DeviceItem(device = dev, state = itemState) { /* connect via address lookup */ }
                }
            }

            // ── Placeholder cuando no hay nada ────────────────────────────────
            if (!isDiscovering && pairedDevices.isEmpty() && discoveredDevices.isEmpty()) {
                item(key = "empty_state") { EmptyDeviceList() }
            }

            // ── Diálogo de permisos ───────────────────────────────────────────
            permissionState?.let { ps ->
                item(key = "permission_dialog") {
                    BluetoothPermissionDialog(
                        permissionState = ps,
                        onRequest       = { permissionLauncher.launch(BLUETOOTH_PERMISSIONS) },
                        onOpenSettings  = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                            permissionState = null
                        },
                        onDismiss       = { permissionState = null }
                    )
                }
            }

            // ── Espacio inferior para FAB ─────────────────────────────────────
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Sub-composables privados
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScanControlRow(
    isDiscovering: Boolean,
    isBluetoothEnabled: Boolean,
    onScan: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Buscar nuevos dispositivos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onScan,
            enabled = isBluetoothEnabled && !isDiscovering,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
        ) {
            if (isDiscovering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(6.dp))
                Text("Buscando…")
            } else {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Buscar")
            }
        }
    }
}

@Composable
private fun BtDisabledBanner(onEnable: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Bluetooth desactivado",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Activa el Bluetooth para buscar adaptadores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            OutlinedButton(
                onClick = onEnable,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) { Text("Activar") }
        }
    }
}

@Composable
private fun ConnectionStateBanner(
    state: ConnectionState,
    onCancel: () -> Unit
) {
    val (bgColor, text) = when (state) {
        is ConnectionState.Connecting   -> MaterialTheme.colorScheme.primaryContainer to "Conectando al adaptador…"
        is ConnectionState.Reconnecting -> MaterialTheme.colorScheme.secondaryContainer to
            "Reconectando (intento ${state.attempt}/${state.maxAttempts})…"
        is ConnectionState.Error        -> MaterialTheme.colorScheme.errorContainer to "Error: ${state.message}"
        else                            -> MaterialTheme.colorScheme.surfaceVariant to ""
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state is ConnectionState.Connecting || state is ConnectionState.Reconnecting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
            TextButton(onClick = onCancel) {
                Text("Cancelar", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ScanningEmptyPlaceholder() {
    val infiniteTransition = rememberInfiniteTransition(label = "ScanPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "PulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.BluetoothSearching,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            "Buscando adaptadores ELM327…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            "Asegúrate de que el adaptador esté encendido\ny en modo de emparejamiento.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyDeviceList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.BluetoothDisabled,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Text(
            "Sin dispositivos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            "Empareja el adaptador ELM327 desde\nAjustes → Bluetooth y luego pulsa \"Buscar\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════════════════════

private val previewPaired = listOf(
    DeviceUiModel("ELM327 v2.1", "00:1D:A5:68:98:8B", isPaired = true,  signalStrength = -58),
    DeviceUiModel("OBDII BT",    "00:1B:DC:F2:03:D0", isPaired = true,  signalStrength = -72)
)
private val previewDiscovered = listOf(
    DeviceUiModel("OBD Adapter",  "00:0C:BF:01:02:03", isPaired = false),
    DeviceUiModel("Desconocido",  "00:AA:BB:CC:DD:EE", isPaired = false)
)

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "Idle – Lista dispositivos",
    widthDp = 380, heightDp = 720)
@Composable
private fun PreviewDeviceScanIdle() {
    MaterialTheme {
        DeviceScanContent(
            connectionState   = ConnectionState.Disconnected,
            pairedDevices     = previewPaired,
            discoveredDevices = previewDiscovered,
            isDiscovering     = false,
            isBluetoothEnabled = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "Scanning – Buscando…",
    widthDp = 380, heightDp = 720)
@Composable
private fun PreviewDeviceScanSearching() {
    MaterialTheme {
        DeviceScanContent(
            connectionState   = ConnectionState.Disconnected,
            pairedDevices     = previewPaired,
            discoveredDevices = emptyList(),
            isDiscovering     = true,
            isBluetoothEnabled = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "Connecting – Conectando…",
    widthDp = 380, heightDp = 720)
@Composable
private fun PreviewDeviceScanConnecting() {
    MaterialTheme {
        DeviceScanContent(
            connectionState    = ConnectionState.Connecting,
            pairedDevices      = previewPaired,
            connectingAddress  = "00:1D:A5:68:98:8B",
            isDiscovering      = false,
            isBluetoothEnabled = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "BT Disabled",
    widthDp = 380, heightDp = 720)
@Composable
private fun PreviewDeviceScanBtOff() {
    MaterialTheme {
        DeviceScanContent(
            connectionState    = ConnectionState.Disconnected,
            pairedDevices      = emptyList(),
            isDiscovering      = false,
            isBluetoothEnabled = false
        )
    }
}
