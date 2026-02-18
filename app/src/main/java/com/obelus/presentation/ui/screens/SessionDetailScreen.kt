package com.obelus.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import com.obelus.data.obd2.Obd2Decoder
import com.obelus.presentation.ui.components.LineChart
import com.obelus.presentation.viewmodel.SessionDetailViewModel
import com.obelus.presentation.viewmodel.ExportStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    viewModel: SessionDetailViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val selectedPid by viewModel.selectedPid.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(exportStatus) {
        if (exportStatus is ExportStatus.Success) {
            Toast.makeText(context, "CSV exportado correctamente", Toast.LENGTH_SHORT).show()
        } else if (exportStatus is ExportStatus.Error) {
            Toast.makeText(context, "Error: ${(exportStatus as ExportStatus.Error).message}", Toast.LENGTH_LONG).show()
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
                    IconButton(onClick = { viewModel.exportToCsv(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar")
                    }
                }
            )
        }
    ) { padding ->
        if (session == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Resumen de la sesión
                SessionSummaryCard(session!!)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Datos del Sensor",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                // Selector de PID a visualizar
                PidSelectorChips(
                    availablePids = viewModel.availablePids,
                    selectedPid = selectedPid,
                    onPidSelected = { viewModel.selectPid(it) },
                    viewModel = viewModel
                )
                
                // Gráfico de línea del PID seleccionado
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = viewModel.getPidName(selectedPid),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (readings.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Sin datos para este sensor en esta sesión")
                            }
                        } else {
                             LineChart(
                                readings = readings,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                // Estadísticas del PID seleccionado
                if (readings.isNotEmpty()) {
                    PidStatisticsCard(viewModel)
                }
            }
        }
    }
}

@Composable
fun SessionSummaryCard(session: ScanSession) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Fecha:", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(session.startTime)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Duración:", style = MaterialTheme.typography.labelMedium)
                val duration = (session.endTime ?: System.currentTimeMillis()) - session.startTime
                val minutes = duration / 60000
                val seconds = (duration % 60000) / 1000
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (session.averageSpeed != null) {
                Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Vel. Media:", style = MaterialTheme.typography.labelMedium)
                    Text(text = "${session.averageSpeed.toInt()} km/h", style = MaterialTheme.typography.bodyMedium)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        availablePids.forEach { pid ->
            FilterChip(
                selected = pid == selectedPid,
                onClick = { onPidSelected(pid) },
                label = { Text(viewModel.getPidName(pid)) }
            )
        }
    }
}

@Composable
fun PidStatisticsCard(viewModel: SessionDetailViewModel) {
    val stats = viewModel.getStatistics()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Mín", String.format("%.1f", stats.min))
            StatItem("Promedio", String.format("%.1f", stats.average))
            StatItem("Máx", String.format("%.1f", stats.max))
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}
