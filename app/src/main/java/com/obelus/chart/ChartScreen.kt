package com.obelus.chart

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obelus.presentation.viewmodel.ScanViewModel

// ─────────────────────────────────────────────────────────────────────────────
// ChartScreen.kt
// Pantalla completa de gráficos en tiempo real.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChartScreen(
    viewModel: ScanViewModel = hiltViewModel()
) {
    val chartData   by viewModel.chartData.collectAsStateWithLifecycle()
    val chartEvents by viewModel.chartEvents.collectAsStateWithLifecycle()
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()

    // Local UI state
    var selectedSignals  by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedType     by remember { mutableStateOf(ChartType.LINE_CHART) }
    var isAutoScaleY     by remember { mutableStateOf(true) }
    var isPlaying        by remember { mutableStateOf(true) }
    var capturedBitmap   by remember { mutableStateOf<Bitmap?>(null) }

    // Auto-select first available signal
    LaunchedEffect(chartData.keys) {
        if (selectedSignals.isEmpty() && chartData.isNotEmpty()) {
            selectedSignals = setOf(chartData.keys.first())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        ChartTopBar(
            isPlaying = isPlaying,
            onPlayPause = {
                isPlaying = !isPlaying
                if (isPlaying) viewModel.startChartRecording(selectedSignals.toList())
                else           viewModel.stopChartRecording()
            },
            onAutoScale = { isAutoScaleY = !isAutoScaleY },
            isAutoScale = isAutoScaleY
        )

        // ── Signal chips ──────────────────────────────────────────────────────
        SignalChipRow(
            signals  = chartData.keys.toList(),
            selected = selectedSignals,
            onToggle = { sig ->
                selectedSignals = if (sig in selectedSignals)
                    selectedSignals - sig else selectedSignals + sig
            }
        )

        // ── Chart type selector ───────────────────────────────────────────────
        ChartTypeSelector(selected = selectedType, onSelect = { selectedType = it })

        // ── Main chart ────────────────────────────────────────────────────────
        val primary   = selectedSignals.firstOrNull()
        val extraKeys = selectedSignals.drop(1)
        val primaryData = if (primary != null) chartData[primary] ?: emptyList() else emptyList()
        val extraData   = extraKeys.associateWith { chartData[it] ?: emptyList() }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .weight(1f),
            shape    = RoundedCornerShape(16.dp),
            colors   = CardDefaults.cardColors(containerColor = Color(0xFF102027))
        ) {
            RealTimeChart(
                data        = primaryData,
                type        = selectedType,
                modifier    = Modifier.fillMaxSize(),
                unit        = primaryData.firstOrNull()?.unit ?: "",
                extraSeries = extraData.takeIf { selectedType == ChartType.COMPARISON_CHART } ?: emptyMap()
            )
        }

        // ── Events / stats strip ──────────────────────────────────────────────
        if (chartEvents.isNotEmpty()) {
            EventsStrip(events = chartEvents.takeLast(5))
        }

        // ── Thumbnail tabs ─────────────────────────────────────────────────────
        val otherSignals = chartData.keys.filter { it !in selectedSignals }
        if (otherSignals.isNotEmpty()) {
            ThumbnailRow(
                signals   = otherSignals,
                chartData = chartData,
                onTap     = { sig -> selectedSignals = setOf(sig) }
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────
@Composable
private fun ChartTopBar(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onAutoScale: () -> Unit,
    isAutoScale: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Default.ShowChart, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Gráficos en Tiempo Real",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text("Canvas · 10-20 Hz · LTTB 500pt",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onAutoScale) {
            Icon(
                if (isAutoScale) Icons.Default.AutoGraph else Icons.Default.LockOpen,
                contentDescription = if (isAutoScale) "Escala automática" else "Escala fija",
                tint = if (isAutoScale) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline
            )
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    HorizontalDivider()
}

// ── Signal chip row ───────────────────────────────────────────────────────────
@Composable
private fun SignalChipRow(
    signals: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    if (signals.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(signals) { _, sig ->
            FilterChip(
                selected = sig in selected,
                onClick  = { onToggle(sig) },
                label    = { Text(sig, fontSize = 12.sp) }
            )
        }
    }
}

// ── Chart type tabs ───────────────────────────────────────────────────────────
@Composable
private fun ChartTypeSelector(selected: ChartType, onSelect: (ChartType) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = ChartType.entries.indexOf(selected),
        edgePadding = 12.dp,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor   = MaterialTheme.colorScheme.primary
    ) {
        ChartType.entries.forEachIndexed { _, type ->
            Tab(
                selected = type == selected,
                onClick  = { onSelect(type) },
                text     = { Text("${type.icon} ${type.displayName}", fontSize = 11.sp) }
            )
        }
    }
}

// ── Events strip ──────────────────────────────────────────────────────────────
@Composable
private fun EventsStrip(events: List<ChartEvent>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(events) { _, event ->
            val (label, color) = when (event) {
                is ChartEvent.SpikeEvent -> {
                    val sev = if (event.severity == SpikeSeverity.CRITICAL) "🔴" else "🟡"
                    "$sev Pico ${event.signalId} ${"%.1f".format(event.value)}" to
                        (if (event.severity == SpikeSeverity.CRITICAL) Color(0xFFF44336)
                         else Color(0xFFFFC107))
                }
                is ChartEvent.OscillationDetected -> "≋ ${event.signalId} ${"%.1f".format(event.frequencyHz)}Hz" to Color(0xFF4FC3F7)
                is ChartEvent.TrendEvent -> {
                    val dir = when (event.direction) {
                        TrendDirection.RISING  -> "↗"; TrendDirection.FALLING -> "↘"; else -> "→"
                    }
                    "$dir ${event.signalId}" to Color(0xFFA5D6A7)
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.2f), border = BorderStroke(1.dp, color)) {
                Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Thumbnail row ─────────────────────────────────────────────────────────────
@Composable
private fun ThumbnailRow(
    signals: List<String>,
    chartData: Map<String, List<ChartPoint>>,
    onTap: (String) -> Unit
) {
    LazyRow(
        contentPadding           = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement    = Arrangement.spacedBy(8.dp),
        modifier                 = Modifier.height(82.dp)
    ) {
        itemsIndexed(signals) { _, sig ->
            Card(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .clickable { onTap(sig) },
                shape  = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF102027))
            ) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        sig, fontSize = 10.sp,
                        color    = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp, end = 6.dp)
                    )
                    RealTimeChart(
                        data     = chartData[sig] ?: emptyList(),
                        modifier = Modifier.fillMaxSize(),
                        theme    = ChartTheme(strokeWidthDp = 1.5f, fillAlpha = 0.12f)
                    )
                }
            }
        }
    }
}
