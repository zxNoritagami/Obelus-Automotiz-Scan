package com.obelus.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obelus.bluetooth.ConnectionState
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.local.entity.DatabaseFile
import com.obelus.data.local.entity.DtcCode
import com.obelus.data.local.entity.SignalReading
import com.obelus.data.local.model.SignalStats
import com.obelus.data.local.model.Status
import com.obelus.presentation.ui.components.*
import com.obelus.presentation.viewmodel.ScanUiState
import com.obelus.presentation.viewmodel.ScanViewModel
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// ScanScreen.kt
// Pantalla principal de escaneo OBD2 en tiempo real.
// Material3 – dark-first (#121212 / #1E1E1E) – sin lógica de negocio en UI.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Punto de entrada Hilt – observa [ScanViewModel] y delega a [ScanContent].
 */
@Composable
fun ScanScreen(
    onNavigateToDeviceScan: () -> Unit = {},
    onNavigateToHistory:    () -> Unit = {},
    onNavigateToDtcs:       () -> Unit = {},
    viewModel: ScanViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Disponible señales para el diálogo de agregar señal
    val availableSignals = remember(state.selectedDatabase) {
        viewModel.getAvailableSignals()
    }
    val availableDbs: List<DatabaseFile> = emptyList() // conectar via VM si se expone

    // Navegar a DeviceScanScreen si se pierde la conexión estando en esta pantalla
    LaunchedEffect(state.connectionState) {
        if (state.connectionState is ConnectionState.Disconnected &&
            state.sessionId == null) {
            // Pequeño delay para evitar navegación involuntaria durante reconexión
            delay(1500)
            if (state.connectionState is ConnectionState.Disconnected) {
                onNavigateToDeviceScan()
            }
        }
    }

    ScanContent(
        state               = state,
        availableSignals    = availableSignals,
        availableDbs        = availableDbs,
        onDisconnect        = { viewModel.disconnect() },
        onLoadDbc           = { /* mostrar file picker → viewModel.loadDatabase(path) */ },
        onStartScan         = { viewModel.startScan() },
        onStopScan          = { viewModel.stopScan() },
        onReadDtcs          = { viewModel.readDtcs() },
        onClearDtc          = { code -> viewModel.clearDtc(code) },
        onExport            = { /* TODO: invocar ExportManager */ },
        onNavigateToHistory = onNavigateToHistory
    )
}

/**
 * Versión stateless de la pantalla de escaneo (facilita previews y tests).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanContent(
    state: ScanUiState             = ScanUiState(),
    availableSignals: List<CanSignal> = emptyList(),
    availableDbs: List<DatabaseFile>  = emptyList(),
    onDisconnect: () -> Unit       = {},
    onLoadDbc: () -> Unit          = {},
    onStartScan: () -> Unit        = {},
    onStopScan: () -> Unit         = {},
    onReadDtcs: () -> Unit         = {},
    onClearDtc: (String) -> Unit   = {},
    onExport: (ExportFormat) -> Unit = {},
    onNavigateToHistory: () -> Unit  = {}
) {
    // ── Diálogos locales ──────────────────────────────────────────────────────
    var showSelectDb  by remember { mutableStateOf(false) }
    var showAddSignal by remember { mutableStateOf(false) }
    var showExport    by remember { mutableStateOf(false) }
    var scanMode      by remember { mutableStateOf(ScanMode.GENERIC_PIDS) }
    var customIds     by remember { mutableStateOf(emptySet<Long>()) }

    // ── Cronómetro de sesión ──────────────────────────────────────────────────
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var elapsedMs      by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.isScanning) {
        if (state.isScanning) {
            sessionStartMs = System.currentTimeMillis()
            while (state.isScanning) {
                elapsedMs = System.currentTimeMillis() - sessionStartMs
                delay(500)
            }
        } else { elapsedMs = 0L }
    }

    // ── FPS estimado ──────────────────────────────────────────────────────────
    val fps = if (state.isScanning && elapsedMs > 0)
        (state.readings.size / (elapsedMs / 1000f)).coerceIn(0f, 10f) else 0f

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            ScanHeader(
                connectionState  = state.connectionState,
                detectedProtocol = state.detectedProtocol,
                dbcName          = state.selectedDatabase?.fileName,
                onDisconnect     = onDisconnect,
                onLoadDbc        = { showSelectDb = true }
            )
        },
        bottomBar = {
            ScanFooter(
                framesPerSecond  = fps,
                sessionElapsedMs = elapsedMs,
                readingCount     = state.readings.size,
                isScanning       = state.isScanning,
                onExport         = { showExport = true }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── Mensaje de error ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                state.errorMessage?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Sugerencia de protocolo ───────────────────────────────────────
            state.protocolSuggestion?.let { tip ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1B3A4B)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(18.dp))
                        Text(tip, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB3E5FC))
                    }
                }
            }

            // ── Controles Iniciar/Detener ─────────────────────────────────────
            ScanControls(
                isScanning       = state.isScanning,
                isConnected      = state.connectionState is ConnectionState.Connected,
                selectedMode     = scanMode,
                hasDbcLoaded     = state.selectedDatabase != null,
                customSignalCount = customIds.size,
                onStart          = onStartScan,
                onStop           = onStopScan,
                onModeChange     = { scanMode = it },
                onAddSignal      = { showAddSignal = true }
            )

            // ── Contenido principal ───────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // Estado vacío / connecting
                if (!state.isScanning && state.readings.isEmpty()) {
                    item(key = "placeholder") {
                        ScanPlaceholder(
                            connectionState = state.connectionState,
                            isConnected = state.connectionState is ConnectionState.Connected
                        )
                    }
                }

                // Lecturas en tiempo real
                val latestBySignal = state.readings
                    .groupBy { it.pid }
                    .mapValues { (_, v) -> v.maxByOrNull { it.timestamp }!! }

                items(latestBySignal.values.toList(), key = { it.pid }) { reading ->
                    val stats = state.stats[reading.name]
                    EnhancedReadingItem(reading = reading, stats = stats)
                }

                // Sección DTC colapsable
                item(key = "dtc_section") {
                    DtcSection(
                        dtcs        = state.dtcs,
                        isLoading   = false,
                        onReadDtcs  = onReadDtcs,
                        onClearAll  = { state.dtcs.forEach { onClearDtc(it.code) } },
                        onClearSingle = onClearDtc
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    // ── Diálogos ──────────────────────────────────────────────────────────────
    if (showSelectDb) {
        SelectDatabaseDialog(
            databases    = availableDbs,
            selectedFile = state.selectedDatabase,
            onSelect     = { /* viewModel.selectDatabase(it) */ },
            onDismiss    = { showSelectDb = false }
        )
    }
    if (showAddSignal) {
        AddSignalDialog(
            availableSignals = availableSignals,
            selectedSignals  = customIds,
            onAdd            = { ids -> customIds = ids },
            onDismiss        = { showAddSignal = false }
        )
    }
    if (showExport) {
        ExportDialog(
            sessionId    = state.sessionId,
            readingCount = state.readings.size,
            onExport     = onExport,
            onDismiss    = { showExport = false }
        )
    }
}

// ── Componentes privados ──────────────────────────────────────────────────────

/**
 * ReadingItem mejorado con barra de progreso y colores de alerta.
 */
@Composable
private fun EnhancedReadingItem(
    reading: SignalReading,
    stats: SignalStats?     = null
) {
    val value = reading.value
    val min   = stats?.min ?: 0f
    val max   = stats?.max ?: (value * 1.5f).coerceAtLeast(1f)

    // Estado semafórico basado en posición relativa en rango
    val fraction  = if (max > min) ((value - min) / (max - min)).coerceIn(0f, 1f) else 0f
    val alertStatus = when {
        fraction > 0.85f -> Status.CRITICAL
        fraction > 0.70f -> Status.HIGH
        fraction < 0.10f -> Status.LOW
        else             -> Status.NORMAL
    }
    val barColor = when (alertStatus) {
        Status.CRITICAL -> Color(0xFFE53935)
        Status.HIGH     -> Color(0xFFFFB300)
        Status.LOW      -> Color(0xFF42A5F5)
        Status.NORMAL   -> Color(0xFF00C853)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = reading.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // Valor animado
                val animatedVal by animateFloatAsState(
                    targetValue = value,
                    animationSpec = tween(300),
                    label = "ValueAnim_${reading.pid}"
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "%.2f".format(animatedVal),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = barColor,
                        fontSize = 22.sp
                    )
                    if (reading.unit.isNotBlank()) {
                        Text(
                            reading.unit,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Barra de progreso min-max
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color              = barColor,
                trackColor         = barColor.copy(alpha = 0.15f),
                strokeCap          = androidx.compose.ui.graphics.StrokeCap.Round
            )

            // Stats min/max
            stats?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("min %.1f".format(it.min), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Text("avg %.1f".format(it.avg), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Text("max %.1f".format(it.max), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ScanPlaceholder(
    connectionState: ConnectionState,
    isConnected: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (connectionState) {
                is ConnectionState.Connecting, is ConnectionState.Reconnecting -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Conectando…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ConnectionState.Connected -> {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "Pulsa \"Iniciar Escaneo\" para comenzar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "Sin conexión activa",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════════════════════

private fun fakeReading(pid: String, name: String, value: Float, unit: String) = SignalReading(
    sessionId = 1, pid = pid, name = name, value = value, unit = unit,
    timestamp = System.currentTimeMillis()
)

private val previewState = ScanUiState(
    connectionState  = ConnectionState.Connected("ELM327 v2.1", "00:1D:A5"),
    isScanning       = true,
    readings         = listOf(
        fakeReading("0C", "RPM",           2340f, "rpm"),
        fakeReading("0D", "Velocidad",      87f,  "km/h"),
        fakeReading("05", "Temp. Motor",    92f,  "°C"),
        fakeReading("04", "Carga Motor",    54f,  "%"),
        fakeReading("11", "Posición Acele", 32f,  "%")
    ),
    dtcs    = listOf(
        DtcCode("P0133", "O2 Sensor Slow Response Bank 1", 'P', true, false, false, null, null, null, null)
    ),
    stats = mapOf(
        "RPM"        to com.obelus.data.local.model.SignalStats(avg=1800f, max=4500f, min=700f),
        "Velocidad"  to com.obelus.data.local.model.SignalStats(avg=75f,   max=120f,  min=0f)
    )
)

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ScanScreen – Scanning",
    widthDp = 390, heightDp = 820)
@Composable
private fun PreviewScanScanning() {
    MaterialTheme { ScanContent(state = previewState) }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ScanScreen – Idle Connected",
    widthDp = 390, heightDp = 820)
@Composable
private fun PreviewScanIdle() {
    MaterialTheme {
        ScanContent(
            state = ScanUiState(
                connectionState = ConnectionState.Connected("ELM327 v2.1", "00:1D:A5"),
                isScanning      = false
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ScanScreen – Error",
    widthDp = 390, heightDp = 820)
@Composable
private fun PreviewScanError() {
    MaterialTheme {
        ScanContent(
            state = ScanUiState(
                connectionState = ConnectionState.Error("Timeout de protocolo"),
                errorMessage    = "Protocolo no detectado. Verifique el adaptador."
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ScanScreen – Disconnected",
    widthDp = 390, heightDp = 820)
@Composable
private fun PreviewScanDisconnected() {
    MaterialTheme {
        ScanContent(state = ScanUiState(connectionState = ConnectionState.Disconnected))
    }
}
