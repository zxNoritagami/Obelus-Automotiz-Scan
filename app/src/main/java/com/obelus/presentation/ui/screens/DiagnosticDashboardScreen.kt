package com.obelus.presentation.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.obelus.domain.model.DiagnosticFinding
import com.obelus.presentation.viewmodel.DiagnosticViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticDashboardScreen(viewModel: DiagnosticViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Análisis de Diagnóstico", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.exportPdfReport(context, "JTDEPRAE3MJ123456", "Toyota Corolla SE 2021") }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Exportar PDF", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Health Score Section
                    item {
                        HealthScoreCard(uiState.vehicleHealthScore)
                    }

                    // 2. Critical Alert
                    if (uiState.hasCriticalAlert) {
                        item {
                            CriticalAlertBanner()
                        }
                    }

                    // 3. Probability Chart
                    if (uiState.findings.isNotEmpty()) {
                        item {
                            Text(
                                "Distribución de Probabilidades",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ProbabilityBarChart(uiState.findings)
                        }
                    }

                    // 4. Findings List
                    item {
                        Text(
                            "Hallazgos Detectados",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(uiState.findings) { finding ->
                        FindingItem(finding)
                    }
                    
                    item {
                        Button(
                            onClick = { viewModel.exportPdfReport(context, "JTDEPRAE3MJ123456", "Toyota Corolla SE 2021") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("GENERAR REPORTE PDF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthScoreCard(score: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Estado General del Vehículo", style = MaterialTheme.typography.labelLarge)
            Text(
                "$score%",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = when {
                    score > 80 -> Color(0xFF4CAF50)
                    score > 50 -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
            )
            LinearProgressIndicator(
                progress = score / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = when {
                    score > 80 -> Color(0xFF4CAF50)
                    score > 50 -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
            )
        }
    }
}

@Composable
fun CriticalAlertBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "ALERTA CRÍTICA: Se requiere revisión inmediata del sistema.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ProbabilityBarChart(findings: List<DiagnosticFinding>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            factory = { context ->
                BarChart(context).apply {
                    description.isEnabled = false
                    setDrawGridBackground(false)
                    legend.isEnabled = false
                    xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    axisLeft.setDrawGridLines(true)
                    axisRight.isEnabled = false
                    axisLeft.axisMinimum = 0f
                    axisLeft.axisMaximum = 100f
                }
            },
            update = { chart ->
                val entries = findings.mapIndexed { index, finding ->
                    BarEntry(index.toFloat(), (finding.posteriorProbability * 100).toFloat())
                }
                val dataSet = BarDataSet(entries, "Probabilidad %").apply {
                    color = AndroidColor.CYAN
                    valueTextColor = AndroidColor.WHITE
                    valueTextSize = 10f
                }
                chart.data = BarData(dataSet)
                chart.xAxis.valueFormatter = IndexAxisValueFormatter(findings.map { it.dtcCode })
                chart.invalidate()
            }
        )
    }
}

@Composable
fun FindingItem(finding: DiagnosticFinding) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (finding.severityLevel >= 5) 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) 
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = finding.dtcCode,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${(finding.posteriorProbability * 100).toInt()}% Confianza",
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = finding.probableCause, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Severidad: ${finding.severityLevel}/5",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (finding.severityLevel >= 4) Color.Red else Color.Gray
                )
                if (finding.isRootCandidate) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Posible Causa Raíz", fontSize = 10.sp) }
                    )
                }
            }
        }
    }
}
