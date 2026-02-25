package com.obelus.presentation.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.canlog.DecodingState
import com.obelus.data.canlog.ImportState
import com.obelus.data.local.entity.CanFrameEntity
import com.obelus.data.local.entity.DbcDefinition
import com.obelus.data.local.entity.DecodedSignal
import com.obelus.presentation.viewmodel.LogViewerViewModel
import com.obelus.presentation.viewmodel.ViewMode
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// PALETA DE COLORES ESTILO SavvyCAN
// ─────────────────────────────────────────────────────────────────────────────
private val BgDark        = Color(0xFF0D1117)
private val BgPanel       = Color(0xFF161B22)
private val BgRow         = Color(0xFF1C2128)
private val BgRowAlt      = Color(0xFF21262D)
private val AccentGreen   = Color(0xFF3FB950)
private val AccentBlue    = Color(0xFF58A6FF)
private val AccentOrange  = Color(0xFFD29922)
private val AccentPurple  = Color(0xFFBC8CFF)
private val TextPrimary   = Color(0xFFE6EDF3)
private val TextMuted     = Color(0xFF8B949E)
private val DividerColor  = Color(0xFF30363D)

// Ancho fijo de columnas (tabla raw)
private val TS_WIDTH      = 80.dp
private val IDX_WIDTH     = 44.dp
private val ID_WIDTH      = 72.dp
private val DLC_WIDTH     = 40.dp
private val DATA_WIDTH    = 200.dp
private val BUS_WIDTH     = 36.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    viewModel: LogViewerViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState      by viewModel.uiState.collectAsState()
    val importState  by viewModel.importState.collectAsState()
    val decodingState by viewModel.decodingState.collectAsState()
    val decodedSignals by viewModel.decodedSignals.collectAsState()
    val scope = rememberCoroutineScope()

    val logLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importLog(it, it.lastPathSegment ?: "log") }
    }

    val dbcLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadDbc(it) }
    }

    var showFilterDialog    by remember { mutableStateOf(false) }
    var showDbcSelector     by remember { mutableStateOf(false) }
    var selectedSignalDetail by remember { mutableStateOf<DecodedSignal?>(null) }
    var signalHistory       by remember { mutableStateOf<List<DecodedSignal>>(emptyList()) }

    val lazyListState = rememberLazyListState()

    var isImporting by remember { mutableStateOf(false) } // Simulate import state for UI test
    var importProgress by remember { mutableFloatStateOf(0f) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }

    Scaffold(
        containerColor = Color(0xFF0A0A0F),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "CAN BUS ANALYZER",
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                        )
                        if (uiState.sessionId != null) {
                            Text(
                                "SESSION: ${uiState.sessionId.toString().takeLast(6).uppercase()} | FRAMES: ${uiState.totalFrames}",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    // Modern DBC Dropdown Selector (Simulated Action)
                    Surface(
                        color = Color(0xFF14141A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp).clickable { showDbcSelector = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Icon(Icons.Default.UploadFile, contentDescription = "DBC", tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(uiState.appliedDbcDefinition?.name ?: "LOAD DBC", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filtrar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0F))
            )
        },
        floatingActionButton = {
            // Neon Import Button
            FloatingActionButton(
                onClick = {
                    // Simulate Loading Overlay for testing visual
                    scope.launch {
                        isImporting = true
                        for (i in 0..100) {
                            importProgress = i / 100f
                            kotlinx.coroutines.delay(20)
                        }
                        isImporting = false
                        logLauncher.launch(arrayOf("*/*", "text/*", "application/*"))
                    }
                },
                containerColor = NeonCyan,
                contentColor = Color.Black,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = "Importar log")
            }
        },
        bottomBar = {
            if (uiState.sessionId != null) {
                com.obelus.ui.components.logs.LogTimeline(
                    isPlaying = false,
                    progress = scrubProgress,
                    totalTimeMs = 60000L, // Mock 1 min
                    onPlayPause = { /* TODO */ },
                    onScrub = { scrubProgress = it }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0A0A0F))
        ) {
            Column {
                // View Mode Toggle (RAW / DECODED) - Hacker Style
                if (uiState.appliedDbcDefinition != null && uiState.decodingProgress == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        SegmentedControl(
                            items = listOf("RAW HEX", "DECODED SIGNAL"),
                            selectedIndex = if (uiState.viewMode == ViewMode.RAW) 0 else 1,
                            onItemSelection = { index ->
                                if (index == 0 && uiState.viewMode != ViewMode.RAW) viewModel.toggleViewMode()
                                else if (index == 1 && uiState.viewMode != ViewMode.DECODED) viewModel.toggleViewMode()
                            }
                        )
                    }
                }

                // Error status
                ImportStatusBar(importState, onDismiss = viewModel::clearError)

                // Main Matrix
                when {
                    uiState.sessionId == null && !isImporting -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = Color.Gray.copy(0.2f), modifier = Modifier.size(120.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("AWAITING LOG DATA...", color = Color.Gray, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                            }
                        }
                    }
                    uiState.viewMode == ViewMode.DECODED && uiState.appliedDbcDefinition != null -> {
                        // DECODED MODE: SignalCards Grid/List
                        if (decodedSignals.isEmpty()) {
                            Text("NO SIGNALS DECODED IN CURRENT VIEWPORT", color = Color.Red, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp))
                        } else {
                            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(decodedSignals) { signal ->
                                    com.obelus.ui.components.logs.SignalCard(
                                        signalName = signal.signalName,
                                        currentValue = signal.calculatedValue,
                                        unit = signal.unit ?: "",
                                        minValue = 0f, // Need historic data for true min/max
                                        maxValue = 100f,
                                        recentData = listOf(signal.calculatedValue, signal.calculatedValue * 1.1f, signal.calculatedValue * 0.9f) // Mock sparkline
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // RAW MODE: HexDumpViewer
                        val rawFrames = uiState.visibleFrames.map { 
                            com.obelus.ui.components.logs.CanFrameRaw(it.timestamp, String.format("%03X", it.canId), it.dlc, it.data)
                        }
                        com.obelus.ui.components.logs.HexDumpViewer(frames = rawFrames)
                    }
                }
            }

            // Futuristic Import Overlay
            if (isImporting || importState is ImportState.Loading) {
                val prog = if (isImporting) importProgress else (importState as? ImportState.Loading)?.progress ?: 0f
                com.obelus.ui.components.logs.DbcImportOverlay(
                    isImporting = true,
                    progress = prog,
                    signalsFound = (prog * 150).toInt() // Fake count animation
                )
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────

    if (showFilterDialog) {
        FilterDialog(
            currentMinId = "%03X".format(uiState.filterMinId),
            currentMaxId = "%03X".format(uiState.filterMaxId),
            onApply = { min, max ->
                viewModel.applyIdFilter(min, max)
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }

    if (showDbcSelector) {
        DbcSelectorSheet(
            definitions    = uiState.availableDbcDefinitions,
            currentDbc     = uiState.appliedDbcDefinition,
            hasSession     = uiState.sessionId != null,
            onApply        = { def ->
                viewModel.applyDbcDefinition(def)
                showDbcSelector = false
            },
            onDismiss      = { showDbcSelector = false }
        )
    }

    selectedSignalDetail?.let { signal ->
        SignalDetailDialog(
            signal  = signal,
            history = signalHistory,
            onDismiss = { selectedSignalDetail = null; signalHistory = emptyList() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DBC SELECTOR SHEET (Modal Bottom Sheet)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DbcSelectorSheet(
    definitions: List<DbcDefinition>,
    currentDbc: DbcDefinition?,
    hasSession: Boolean,
    onApply: (DbcDefinition) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentDbc) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgPanel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Aplicar definición DBC",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Selecciona una definición del DBC Editor para decodificar las señales del log importado.",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (!hasSession) {
                Surface(
                    color = AccentOrange.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Primero importa un log CAN", color = AccentOrange, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (definitions.isEmpty()) {
                Text(
                    "No hay definiciones DBC disponibles.\nCrea una en el Editor DBC.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(definitions, key = { it.id }) { def ->
                        val isSelected = selected?.id == def.id
                        Surface(
                            color = if (isSelected) AccentPurple.copy(alpha = 0.18f) else BgRow,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = def }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick  = { selected = def },
                                    colors   = RadioButtonDefaults.colors(selectedColor = AccentPurple)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(def.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(
                                        "${def.signalCount} señal(es) · ${def.protocol}" +
                                            if (def.isBuiltIn) " · Built-in" else " · Custom",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { selected?.let(onApply) },
                    enabled = selected != null && hasSession,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Decodificar log")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VIEW MODE TOGGLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ViewModeToggle(mode: ViewMode, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPanel)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Vista:", color = TextMuted, fontSize = 11.sp)

        FilterChip(
            selected = mode == ViewMode.RAW,
            onClick  = { if (mode != ViewMode.RAW) onToggle() },
            label    = { Text("Tramas crudas", fontSize = 11.sp) },
            leadingIcon = if (mode == ViewMode.RAW) {
                { Icon(Icons.Default.TableRows, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else null,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                selectedLabelColor = AccentBlue
            )
        )

        FilterChip(
            selected = mode == ViewMode.DECODED,
            onClick  = { if (mode != ViewMode.DECODED) onToggle() },
            label    = { Text("Señales DBC", fontSize = 11.sp) },
            leadingIcon = if (mode == ViewMode.DECODED) {
                { Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else null,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentPurple.copy(alpha = 0.2f),
                selectedLabelColor = AccentPurple
            )
        )
    }
    HorizontalDivider(color = DividerColor)
}

// ─────────────────────────────────────────────────────────────────────────────
// DECODING PROGRESS BAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DecodingProgressBar(progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPanel)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AccentPurple
                )
                Spacer(Modifier.width(8.dp))
                Text("Decodificando señales DBC...", color = AccentPurple, fontSize = 11.sp)
            }
            Text("${(progress * 100).toInt()}%", color = TextMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = AccentPurple,
            trackColor = DividerColor
        )
    }
    HorizontalDivider(color = DividerColor)
}

// ─────────────────────────────────────────────────────────────────────────────
// DECODED SIGNAL LIST (summary cards)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DecodedSignalList(
    signals: List<DecodedSignal>,
    onSignalClick: (DecodedSignal) -> Unit
) {
    if (signals.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("Sin señales decodificadas", color = TextPrimary, fontSize = 15.sp)
                Text(
                    "Verifica que el CAN ID de las señales\ncoincida con los frames del log",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(signals, key = { it.signalId }) { signal ->
            SignalSummaryCard(signal = signal, onClick = { onSignalClick(signal) })
        }
    }
}

@Composable
private fun SignalSummaryCard(signal: DecodedSignal, onClick: () -> Unit) {
    Surface(
        color = BgRow,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    signal.signalName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "CAN ID: ${signal.canId}",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            // Value
            Column(horizontalAlignment = Alignment.End) {
                val valueStr = "%.3f".format(signal.calculatedValue).trimEnd('0').trimEnd('.')
                Text(
                    valueStr,
                    color = AccentGreen,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (!signal.unit.isNullOrBlank()) {
                    Text(signal.unit, color = TextMuted, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            // Mini sparkline (placeholder — filled by real data only in detail view)
            MiniSparkline(
                values = listOf(signal.calculatedValue),
                modifier = Modifier
                    .size(width = 48.dp, height = 24.dp)
            )

            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Detalle",
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MINI SPARKLINE CANVAS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = AccentGreen
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) {
            // Draw a single dot
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(color = lineColor, radius = 3f, center = Offset(cx, cy))
            return@Canvas
        }

        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).coerceAtLeast(0.001f)

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i.toFloat() / (values.size - 1) * size.width
            val y = size.height - ((v - minV) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 1.5.dp.toPx()))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SIGNAL DETAIL DIALOG (fullscreen with LineChart)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SignalDetailDialog(
    signal: DecodedSignal,
    history: List<DecodedSignal>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            color = BgPanel,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(signal.signalName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("CAN ID: ${signal.canId}", color = TextMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }
                HorizontalDivider(color = DividerColor)

                val values = history.map { it.calculatedValue }
                val unit   = signal.unit ?: ""

                // Current / min / max summary
                if (values.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBox("Último",  values.last(),  unit, AccentGreen)
                        StatBox("Mínimo",  values.min(),   unit, AccentBlue)
                        StatBox("Máximo",  values.max(),   unit, AccentOrange)
                    }
                    HorizontalDivider(color = DividerColor)
                }

                // Full chart
                if (values.size >= 2) {
                    Text(
                        "Historial (${values.size} muestras)",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        val minV  = values.min()
                        val maxV  = values.max()
                        val range = (maxV - minV).coerceAtLeast(0.001f)
                        val path  = Path()

                        values.forEachIndexed { i, v ->
                            val x = i.toFloat() / (values.size - 1) * size.width
                            val y = size.height - ((v - minV) / range) * size.height
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        // Grid line at min
                        drawLine(DividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                        drawPath(path, color = AccentGreen, style = Stroke(width = 2.dp.toPx()))
                    }
                    HorizontalDivider(color = DividerColor)
                }

                // Last 50 values table
                Text(
                    "Últimas muestras",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    val last50 = history.takeLast(50).reversed()
                    itemsIndexed(last50, key = { _, s -> s.id }) { _, s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val ms = s.timestamp / 1000
                            Text(
                                "${ms / 1000}.${"%03d".format(ms % 1000)} s",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "${"%.4f".format(s.calculatedValue)} $unit",
                                color = AccentGreen,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        HorizontalDivider(color = DividerColor.copy(alpha = 0.4f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: Float, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 9.sp)
        Text(
            "%.3f".format(value).trimEnd('0').trimEnd('.') + if (unit.isNotEmpty()) " $unit" else "",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LEGACY / SHARED COMPONENTS (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ImportStatusBar(state: ImportState, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = state is ImportState.Success || state is ImportState.Error,
        enter = fadeIn(tween(300)),
        exit  = fadeOut(tween(300))
    ) {
        when (state) {
            is ImportState.Success -> Surface(
                color = AccentGreen.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "✅ ${state.frameCount} frames importados (${state.format.name})",
                        color = AccentGreen, fontSize = 12.sp
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
            is ImportState.Error -> Surface(
                color = Color(0xFFFFA67A).copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.message, color = AccentOrange, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun ActiveFilterChip(minId: Int, maxId: Int, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPanel)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.FilterList, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Filtro activo: 0x%03X – 0x%03X".format(minId, maxId),
            color = AccentBlue, fontSize = 11.sp
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClear, contentPadding = PaddingValues(0.dp)) {
            Text("Limpiar", color = AccentBlue, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FrameTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPanel)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("#",       IDX_WIDTH)
        HeaderCell("Tiempo",  TS_WIDTH)
        HeaderCell("ID",      ID_WIDTH)
        HeaderCell("DLC",     DLC_WIDTH)
        HeaderCell("Datos",   DATA_WIDTH)
        HeaderCell("Bus",     BUS_WIDTH)
    }
    HorizontalDivider(color = DividerColor)
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun FrameList(
    frames: List<CanFrameEntity>,
    listState: LazyListState,
    dbcLoaded: Boolean,
    decodeSignals: (CanFrameEntity) -> Map<String, String>,
    baseTimestamp: Long
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(
            items = frames,
            key   = { _, frame -> frame.id }
        ) { idx, frame ->
            FrameRow(
                index         = idx,
                frame         = frame,
                baseTimestamp = baseTimestamp,
                dbcLoaded     = dbcLoaded,
                decodeSignals = decodeSignals,
                isAlt         = idx % 2 == 1
            )
        }
    }
}

@Composable
private fun FrameRow(
    index: Int,
    frame: CanFrameEntity,
    baseTimestamp: Long,
    dbcLoaded: Boolean,
    decodeSignals: (CanFrameEntity) -> Map<String, String>,
    isAlt: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isAlt) BgRowAlt else BgRow)
            .clickable(enabled = dbcLoaded) { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${index + 1}",
                color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(IDX_WIDTH)
            )
            val deltaSecs = (frame.timestamp - baseTimestamp) / 1_000_000.0
            Text(
                "%8.3f".format(deltaSecs),
                color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(TS_WIDTH)
            )
            val idStr = if (frame.isExtended) "%08X".format(frame.canId) else "%03X".format(frame.canId)
            Text(
                idStr,
                color = AccentBlue, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(ID_WIDTH)
            )
            val dlc = frame.dataHex.replace(" ", "").length / 2
            Text(
                dlc.toString(),
                color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(DLC_WIDTH)
            )
            Text(
                frame.dataHex,
                color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(DATA_WIDTH)
            )
            Text(
                frame.bus.toString(),
                color = AccentOrange, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(BUS_WIDTH)
            )
        }

        // Expanded DBC signals (legacy file-based DBC)
        AnimatedVisibility(visible = expanded && dbcLoaded) {
            val signals = remember(frame.id) { decodeSignals(frame) }
            if (signals.isEmpty()) {
                Text(
                    "  Sin señales DBC para ID ${"%03X".format(frame.canId)}",
                    color = TextMuted, fontSize = 10.sp,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgPanel)
                        .padding(start = 24.dp, top = 4.dp, bottom = 6.dp, end = 8.dp)
                ) {
                    signals.forEach { (name, value) ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                name,
                                color = AccentOrange, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(160.dp)
                            )
                            Text(value, color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = DividerColor.copy(alpha = 0.3f), thickness = 0.5.dp)
    }
}

@Composable
private fun PaginationBar(onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPanel)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Anterior", color = AccentBlue, fontSize = 12.sp)
        }
        Text("200 frames/página", color = TextMuted, fontSize = 10.sp)
        TextButton(onClick = onNext) {
            Text("Siguiente", color = AccentBlue, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun LoadingOverlay(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                progress = progress,
                color = AccentPurple,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Importando... ${(progress * 100).toInt()}%",
                color = TextPrimary, fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Sin log cargado", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Importa un archivo CRTD, CSV o PCAN .trc\nusando el botón inferior derecho",
            color = TextMuted, fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onImport,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Importar log CAN")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDialog(
    currentMinId: String,
    currentMaxId: String,
    onApply: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var minId by remember { mutableStateOf(currentMinId) }
    var maxId by remember { mutableStateOf(currentMaxId) }
    val minError = minId.toIntOrNull(16) == null && minId.isNotEmpty()
    val maxError = maxId.toIntOrNull(16) == null && maxId.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPanel,
        title = { Text("Filtrar por CAN ID", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ingresa los IDs en hexadecimal (ej: 000, 7FF)", color = TextMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value         = minId,
                    onValueChange = { minId = it.uppercase().take(8) },
                    label         = { Text("ID Mínimo (HEX)", color = TextMuted) },
                    isError       = minError,
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor   = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentBlue,
                        errorBorderColor   = AccentOrange
                    )
                )
                OutlinedTextField(
                    value         = maxId,
                    onValueChange = { maxId = it.uppercase().take(8) },
                    label         = { Text("ID Máximo (HEX)", color = TextMuted) },
                    isError       = maxError,
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor   = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentBlue,
                        errorBorderColor   = AccentOrange
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onApply(minId.ifEmpty { "000" }, maxId.ifEmpty { "7FF" }) },
                enabled  = !minError && !maxError
            ) {
                Text("Aplicar", color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextMuted)
            }
        }
    )
}
