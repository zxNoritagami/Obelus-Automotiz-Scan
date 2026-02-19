package com.obelus.presentation.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.canlog.ImportState
import com.obelus.data.local.entity.CanFrameEntity
import com.obelus.presentation.viewmodel.LogViewerViewModel

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
private val TextPrimary   = Color(0xFFE6EDF3)
private val TextMuted     = Color(0xFF8B949E)
private val DividerColor  = Color(0xFF30363D)

// Ancho fijo de columnas (tabla)
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
    val uiState     by viewModel.uiState.collectAsState()
    val importState by viewModel.importState.collectAsState()

    // Launchers para selección de archivos
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

    var showFilterDialog  by remember { mutableStateOf(false) }
    val lazyListState      = rememberLazyListState()

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Log Viewer",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (uiState.sessionId != null) {
                            Text(
                                "Sesión: ${uiState.sessionId} • ${uiState.totalFrames} frames",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                actions = {
                    // Indicador DBC
                    if (uiState.dbcLoaded) {
                        Surface(
                            color = AccentGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                "DBC ✓",
                                color = AccentGreen,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    // Botón filtros
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filtrar", tint = TextPrimary)
                    }
                    // Botón cargar DBC
                    IconButton(onClick = { dbcLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.Description, contentDescription = "Cargar DBC", tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPanel)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { logLauncher.launch(arrayOf("*/*", "text/*", "application/*")) },
                containerColor = AccentBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = "Importar log")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgDark)
        ) {
            Column {
                // ── Barra de estado de importación ────────────────────────────
                ImportStatusBar(importState, onDismiss = viewModel::clearError)

                // ── Toolbar de filtros activos ─────────────────────────────────
                if (uiState.filterMinId != 0 || uiState.filterMaxId != 0x1FFFFFFF) {
                    ActiveFilterChip(
                        minId = uiState.filterMinId,
                        maxId = uiState.filterMaxId,
                        onClear = viewModel::clearIdFilter
                    )
                }

                // ── Encabezado de columnas ─────────────────────────────────────
                if (uiState.visibleFrames.isNotEmpty()) {
                    FrameTableHeader()
                }

                // ── Tabla de frames (LazyColumn) ───────────────────────────────
                if (uiState.sessionId == null && importState !is ImportState.Loading) {
                    EmptyState(
                        onImport = { logLauncher.launch(arrayOf("*/*")) }
                    )
                } else {
                    FrameList(
                        frames       = uiState.visibleFrames,
                        listState    = lazyListState,
                        dbcLoaded    = uiState.dbcLoaded,
                        decodeSignals = viewModel::decodeSignals,
                        baseTimestamp = uiState.visibleFrames.firstOrNull()?.timestamp ?: 0L
                    )

                    // ── Paginación ─────────────────────────────────────────────
                    if (uiState.visibleFrames.isNotEmpty()) {
                        PaginationBar(
                            onPrev = viewModel::prevPage,
                            onNext = viewModel::nextPage
                        )
                    }
                }
            }

            // Loading overlay
            if (importState is ImportState.Loading) {
                val progress = (importState as ImportState.Loading).progress
                LoadingOverlay(progress)
            }
        }
    }

    // ── Diálogo de filtros ─────────────────────────────────────────────────────
    if (showFilterDialog) {
        FilterDialog(
            currentMinId = "%03X".format(uiState.filterMinId),
            currentMaxId = "%03X".format(uiState.filterMaxId),
            onApply      = { min, max ->
                viewModel.applyIdFilter(min, max)
                showFilterDialog = false
            },
            onDismiss    = { showFilterDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENTES INTERNOS
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
    Divider(color = DividerColor)
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
        // ── Fila principal ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // #
            Text(
                "${index + 1}",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(IDX_WIDTH)
            )
            // Timestamp relativo
            val deltaSecs = (frame.timestamp - baseTimestamp) / 1_000_000.0
            val relTs = "%8.3f".format(deltaSecs)
            Text(
                relTs,
                color = AccentGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(TS_WIDTH)
            )
            // CAN ID
            val idStr = if (frame.isExtended) "%08X".format(frame.canId)
                        else "%03X".format(frame.canId)
            Text(
                idStr,
                color = AccentBlue,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(ID_WIDTH)
            )
            // DLC
            val dlc = frame.dataHex.replace(" ", "").length / 2
            Text(
                dlc.toString(),
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(DLC_WIDTH)
            )
            // Datos HEX
            Text(
                frame.dataHex,
                color = TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(DATA_WIDTH)
            )
            // Bus
            Text(
                frame.bus.toString(),
                color = AccentOrange,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(BUS_WIDTH)
            )
        }

        // ── Señales DBC expandidas ─────────────────────────────────────────
        AnimatedVisibility(visible = expanded && dbcLoaded) {
            val signals = remember(frame.id) { decodeSignals(frame) }
            if (signals.isEmpty()) {
                Text(
                    "  Sin señales DBC para ID ${"%03X".format(frame.canId)}",
                    color = TextMuted,
                    fontSize = 10.sp,
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
                                color = AccentOrange,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(160.dp)
                            )
                            Text(
                                value,
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Divider(color = DividerColor.copy(alpha = 0.3f), thickness = 0.5.dp)
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
                progress = { progress },
                color = AccentBlue,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Importando... ${(progress * 100).toInt()}%",
                color = TextPrimary,
                fontSize = 14.sp
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
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Sin log cargado", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Importa un archivo CRTD, CSV o PCAN .trc\nusando el botón inferior derecho",
            color = TextMuted,
            fontSize = 13.sp
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
                    value       = minId,
                    onValueChange = { minId = it.uppercase().take(8) },
                    label       = { Text("ID Mínimo (HEX)", color = TextMuted) },
                    isError     = minError,
                    singleLine  = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor    = TextPrimary,
                        unfocusedTextColor  = TextPrimary,
                        focusedBorderColor  = AccentBlue,
                        errorBorderColor    = AccentOrange
                    )
                )
                OutlinedTextField(
                    value       = maxId,
                    onValueChange = { maxId = it.uppercase().take(8) },
                    label       = { Text("ID Máximo (HEX)", color = TextMuted) },
                    isError     = maxError,
                    singleLine  = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor    = TextPrimary,
                        unfocusedTextColor  = TextPrimary,
                        focusedBorderColor  = AccentBlue,
                        errorBorderColor    = AccentOrange
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(minId.ifEmpty { "000" }, maxId.ifEmpty { "7FF" }) },
                enabled = !minError && !maxError
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
