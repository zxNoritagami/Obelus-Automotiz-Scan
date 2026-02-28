package com.obelus.freezeframe

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obelus.domain.model.FreezeFrameData
import com.obelus.presentation.viewmodel.ScanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// FreezeFrameScreen.kt  (mejorado)
// Comparativa lado a lado: valores del freeze frame vs valores actuales.
// ─────────────────────────────────────────────────────────────────────────────

private val TsFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezeFrameScreen(
    dtcCode: String? = null,
    onBack: () -> Unit = {},
    viewModel: ScanViewModel = hiltViewModel()
) {
    val freezeFrames by viewModel.freezeFrames.collectAsStateWithLifecycle()
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()

    // Freeze frames for the selected DTC (or all if dtcCode=null)
    val relevantFrames = if (dtcCode != null)
        freezeFrames.filter { it.dtcCode == dtcCode } else freezeFrames

    // Current live values from scan
    val currentValues = remember(uiState.readings) {
        uiState.readings.associate { it.pid to it.value }
    }

    // Selected freeze frame index (for multi-frame historial)
    var selectedIdx by remember { mutableIntStateOf(relevantFrames.lastIndex.coerceAtLeast(0)) }
    val selectedFrame = relevantFrames.getOrNull(selectedIdx)

    // Comparison and analysis
    val diffs   = remember(selectedFrame, currentValues) {
        if (selectedFrame != null) FreezeFrameAnalyzer.compareWithCurrent(selectedFrame, currentValues)
        else emptyList()
    }
    val findings = remember(selectedFrame) {
        if (selectedFrame != null) FreezeFrameAnalyzer.analyzeConditions(selectedFrame)
        else emptyList()
    }
    val suggested = remember(selectedFrame) {
        if (selectedFrame != null && dtcCode != null)
            FreezeFrameAnalyzer.suggestRelatedDtcs(dtcCode, selectedFrame)
        else emptyList()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Freeze Frame", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(dtcCode ?: "Todos los DTCs",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF29B6F6))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                actions = {
                    // Export button (placeholder — conectar a ReportGenerator)
                    IconButton(onClick = { /* TODO: export to PDF */ }) {
                        Icon(Icons.Default.Share, "Exportar análisis")
                    }
                }
            )
        }
    ) { padding ->
        if (relevantFrames.isEmpty()) {
            EmptyFreezeFrame(Modifier.fillMaxSize().padding(padding), dtcCode)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Historial selector (si hay múltiples) ────────────────────
                if (relevantFrames.size > 1) {
                    item {
                        HistorialSelector(
                            frames    = relevantFrames,
                            selected  = selectedIdx,
                            onSelect  = { selectedIdx = it }
                        )
                    }
                }

                // ── Metadata ──────────────────────────────────────────────────
                selectedFrame?.let { ff ->
                    item { MetadataCard(ff) }
                }

                // ── Hallazgos de análisis ─────────────────────────────────────
                if (findings.isNotEmpty()) {
                    item { FindingsCard(findings) }
                }

                // ── DTCs relacionados sugeridos ───────────────────────────────
                if (suggested.isNotEmpty()) {
                    item { SuggestedDtcsCard(suggested) }
                }

                // ── Comparativa lado a lado ───────────────────────────────────
                item {
                    ComparisonHeader()
                }
                items(diffs) { diff ->
                    ComparisonRow(diff)
                }
            }
        }
    }
}

// ── Historial selector ────────────────────────────────────────────────────────
@Composable
private fun HistorialSelector(
    frames: List<FreezeFrameData>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Card(shape = RoundedCornerShape(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${frames.size} capturas registradas",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                frames.forEachIndexed { idx, _ ->
                    FilterChip(
                        selected = idx == selected,
                        onClick  = { onSelect(idx) },
                        label    = { Text("#${idx + 1}", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }
    }
}

// ── Metadata card ─────────────────────────────────────────────────────────────
@Composable
private fun MetadataCard(ff: FreezeFrameData) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AcUnit, null, tint = Color(0xFF29B6F6), modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("DTC: ${ff.dtcCode}", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFF29B6F6))
                Text(TsFmt.format(Date(ff.timestamp)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(ff.conditionSummary(),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            ff.distanceSinceDtcCleared?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text("$it km", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("desde borrado", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                }
            }
        }
    }
}

// ── Findings card ─────────────────────────────────────────────────────────────
@Composable
private fun FindingsCard(findings: List<ConditionFinding>) {
    Card(shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hallazgos del análisis",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            findings.forEach { finding ->
                FindingRow(finding)
            }
        }
    }
}

@Composable
private fun FindingRow(finding: ConditionFinding) {
    val (bgColor, borderColor, iconTint) = when (finding.severity) {
        FindingSeverity.CRITICAL -> Triple(Color(0x1AF44336), Color(0xFFF44336), Color(0xFFF44336))
        FindingSeverity.WARNING  -> Triple(Color(0x1AFFC107), Color(0xFFFFC107), Color(0xFFFFC107))
        FindingSeverity.INFO     -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline)
    }
    Surface(
        shape  = RoundedCornerShape(8.dp),
        color  = bgColor,
        border = BorderStroke(0.5.dp, borderColor)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                when (finding.severity) {
                    FindingSeverity.CRITICAL -> Icons.Default.Error
                    FindingSeverity.WARNING  -> Icons.Default.Warning
                    FindingSeverity.INFO     -> Icons.Default.Info
                },
                null, tint = iconTint, modifier = Modifier.size(18.dp).padding(top = 2.dp)
            )
            Column {
                Text(finding.title, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, color = iconTint)
                Text(finding.explanation, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline, lineHeight = 14.sp)
            }
        }
    }
}

// ── Suggested DTCs ────────────────────────────────────────────────────────────
@Composable
private fun SuggestedDtcsCard(suggested: List<String>) {
    Card(shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("DTCs relacionados a verificar",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                suggested.forEach { dtc ->
                    AssistChip(onClick = {}, label = { Text(dtc, fontSize = 12.sp) })
                }
            }
        }
    }
}

// ── Comparison header ─────────────────────────────────────────────────────────
@Composable
private fun ComparisonHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("PARÁMETRO", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(2f))
        Text("FREEZE", style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF29B6F6), modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
        Text("Δ", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
        Text("ACTUAL", style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF80CBC4), modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

// ── Comparison row ────────────────────────────────────────────────────────────
@Composable
private fun ComparisonRow(diff: ValueDifference) {
    val (arrow, arrowColor) = when (diff.status) {
        DifferenceStatus.IMPROVED -> "↑" to Color(0xFF4CAF50)
        DifferenceStatus.WORSENED -> "↓" to Color(0xFFF44336)
        DifferenceStatus.STABLE   -> "→" to MaterialTheme.colorScheme.outline
        DifferenceStatus.UNKNOWN  -> "?" to MaterialTheme.colorScheme.outline
    }
    val ffAbnormalColor   = if (diff.wasFreezeFrameAbnormal) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurface
    val curColor = when {
        diff.currentValue.isNaN() -> MaterialTheme.colorScheme.outline
        diff.isCurrentAbnormal    -> Color(0xFFF44336)
        else                      -> Color(0xFF80CBC4)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(2f)) {
            Text(diff.parameterName, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(diff.unit, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
        }
        Text(
            if (diff.freezeFrameValue.isNaN()) "—" else "%.2f".format(diff.freezeFrameValue),
            style  = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            color  = ffAbnormalColor,
            modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center
        )
        Text(
            arrow, fontSize = 16.sp, color = arrowColor, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center
        )
        Text(
            if (diff.currentValue.isNaN()) "—" else "%.2f".format(diff.currentValue),
            style  = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            color  = curColor,
            modifier = Modifier.weight(1.5f), textAlign = TextAlign.End
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyFreezeFrame(modifier: Modifier, dtcCode: String?) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AcUnit, null,
                modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            Text("Sin freeze frame${dtcCode?.let { " para $it" } ?: ""}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            Text("El freeze frame se captura automáticamente al detectar un DTC",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}
