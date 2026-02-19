package com.obelus.presentation.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.export.ExportProgress
import com.obelus.data.local.entity.ScanSession
import com.obelus.presentation.ui.components.LineChart
import com.obelus.presentation.viewmodel.ExportStatus
import com.obelus.presentation.viewmodel.SessionDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Colors for export FABs
// ─────────────────────────────────────────────────────────────────────────────
private val PdfRed    = Color(0xFFEF4444)
private val ExcelGreen = Color(0xFF22C55E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    viewModel: SessionDetailViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val session        by viewModel.session.collectAsState()
    val readings       by viewModel.readings.collectAsState()
    val selectedPid    by viewModel.selectedPid.collectAsState()
    val exportStatus   by viewModel.exportStatus.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val context        = LocalContext.current

    // ── FAB expansion state ─────────────────────────────────────────────
    var fabExpanded by remember { mutableStateOf(false) }

    // ── SAF launchers ───────────────────────────────────────────────────
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let {
            fabExpanded = false
            viewModel.exportToPdf(context, it)
        }
    }

    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        uri?.let {
            fabExpanded = false
            viewModel.exportToExcel(context, it)
        }
    }

    // ── Legacy CSV toast ────────────────────────────────────────────────
    LaunchedEffect(exportStatus) {
        when (exportStatus) {
            is ExportStatus.Success ->
                Toast.makeText(context, "CSV exportado correctamente", Toast.LENGTH_SHORT).show()
            is ExportStatus.Error ->
                Toast.makeText(context, "Error: ${(exportStatus as ExportStatus.Error).message}", Toast.LENGTH_LONG).show()
            else -> {}
        }
    }

    // ── PDF/Excel progress toasts ───────────────────────────────────────
    LaunchedEffect(exportProgress) {
        when (val p = exportProgress) {
            is ExportProgress.Done  -> {
                Toast.makeText(context, "✅ Archivo guardado correctamente", Toast.LENGTH_SHORT).show()
                viewModel.clearExportProgress()
            }
            is ExportProgress.Failed -> {
                Toast.makeText(context, "❌ Error al exportar: ${p.message}", Toast.LENGTH_LONG).show()
                viewModel.clearExportProgress()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.notes ?: "Detalle de Sesión") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    // Legacy CSV share
                    IconButton(onClick = { viewModel.exportToCsv(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar CSV")
                    }
                }
            )
        },
        floatingActionButton = {
            ExportFabGroup(
                expanded      = fabExpanded,
                onToggle      = { fabExpanded = !fabExpanded },
                onExportPdf   = {
                    val filename = "obelus_session_${sessionId}_${System.currentTimeMillis()}.pdf"
                    pdfLauncher.launch(filename)
                },
                onExportExcel = {
                    val filename = "obelus_session_${sessionId}_${System.currentTimeMillis()}.xlsx"
                    excelLauncher.launch(filename)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ── Export progress bar ─────────────────────────────────────
            AnimatedVisibility(
                visible = exportProgress is ExportProgress.InProgress,
                enter   = fadeIn(tween(200)),
                exit    = fadeOut(tween(300))
            ) {
                val prog = exportProgress as? ExportProgress.InProgress
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(prog?.label ?: "Exportando...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${((prog?.pct ?: 0f) * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = prog?.pct ?: 0f,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // ── Main content ────────────────────────────────────────────
            if (session == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SessionSummaryCard(session!!)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Datos del Sensor",
                        style    = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    PidSelectorChips(
                        availablePids = viewModel.availablePids,
                        selectedPid   = selectedPid,
                        onPidSelected = { viewModel.selectPid(it) },
                        viewModel     = viewModel
                    )
                    Card(
                        modifier  = Modifier.fillMaxWidth().padding(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                viewModel.getPidName(selectedPid),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            if (readings.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("Sin datos para este sensor en esta sesión") }
                            } else {
                                LineChart(
                                    readings = readings,
                                    color    = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    if (readings.isNotEmpty()) {
                        PidStatisticsCard(viewModel)
                    }
                    Spacer(Modifier.height(80.dp)) // room for FAB
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Export FAB group — PDF (red) + Excel (green) + main FAB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ExportFabGroup(
    expanded: Boolean,
    onToggle: () -> Unit,
    onExportPdf: () -> Unit,
    onExportExcel: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Sub-FAB: Excel
        AnimatedVisibility(
            visible = expanded,
            enter   = fadeIn(tween(150)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit    = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            ExtendedFloatingActionButton(
                onClick           = onExportExcel,
                containerColor    = ExcelGreen,
                contentColor      = Color.White,
                icon              = { Icon(Icons.Default.TableChart, contentDescription = "Excel") },
                text              = { Text("Excel (.xlsx)", fontWeight = FontWeight.Bold) }
            )
        }

        // Sub-FAB: PDF
        AnimatedVisibility(
            visible = expanded,
            enter   = fadeIn(tween(100)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit    = fadeOut(tween(100)) + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            ExtendedFloatingActionButton(
                onClick           = onExportPdf,
                containerColor    = PdfRed,
                contentColor      = Color.White,
                icon              = { Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF") },
                text              = { Text("PDF Report", fontWeight = FontWeight.Bold) }
            )
        }

        // Main FAB — toggles expansion
        FloatingActionButton(
            onClick        = onToggle,
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                if (expanded) Icons.Default.Close else Icons.Default.FileDownload,
                contentDescription = if (expanded) "Cerrar" else "Exportar"
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Existing composables (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SessionSummaryCard(session: ScanSession) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fecha:", style = MaterialTheme.typography.labelMedium)
                Text(
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(session.startTime)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Duración:", style = MaterialTheme.typography.labelMedium)
                val duration = (session.endTime ?: System.currentTimeMillis()) - session.startTime
                Text(
                    String.format("%02d:%02d", duration / 60000, (duration % 60000) / 1000),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (session.averageSpeed != null) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vel. Media:", style = MaterialTheme.typography.labelMedium)
                    Text("${session.averageSpeed.toInt()} km/h", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PidSelectorChips(
    availablePids: List<String>,
    selectedPid: String,
    onPidSelected: (String) -> Unit,
    viewModel: SessionDetailViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        availablePids.forEach { pid ->
            FilterChip(
                selected = pid == selectedPid,
                onClick  = { onPidSelected(pid) },
                label    = { Text(viewModel.getPidName(pid)) }
            )
        }
    }
}

@Composable
fun PidStatisticsCard(viewModel: SessionDetailViewModel) {
    val stats = viewModel.getStatistics()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DetailStatItem("Mín",     String.format("%.1f", stats.min))
            DetailStatItem("Promedio", String.format("%.1f", stats.average))
            DetailStatItem("Máx",     String.format("%.1f", stats.max))
        }
    }
}

@Composable
fun DetailStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
