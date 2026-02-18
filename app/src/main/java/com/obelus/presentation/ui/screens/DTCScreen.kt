package com.obelus.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.model.DTC
import com.obelus.data.model.DTCSeverity
import com.obelus.presentation.viewmodel.DTCViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DTCScreen(
    viewModel: DTCViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val dtcs by viewModel.dtcs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Códigos de Error") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (dtcs.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearDTCs() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar códigos")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isScanning) {
                FloatingActionButton(
                    onClick = { viewModel.readDTCs() }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Escanear")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isScanning -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Escaneando códigos de error...")
                    }
                }
                dtcs.isEmpty() -> {
                    EmptyDTCState(Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dtcs) { dtc ->
                            DTCItem(dtc)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDTCState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.Refresh, // Placeholder icon
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No se han encontrado códigos o no se ha realizado un escaneo.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pulsa el botón para escanear.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DTCItem(dtc: DTC) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = dtc.code,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )
                SeverityChip(dtc.severity)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dtc.description,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Categoría: ${dtc.category.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SeverityChip(severity: DTCSeverity) {
    val color = when (severity) {
        DTCSeverity.LOW -> Color.Green
        DTCSeverity.MEDIUM -> Color(0xFFFFA500) // Orange
        DTCSeverity.HIGH -> Color.Red
        DTCSeverity.CRITICAL -> Color(0xFF8B0000) // Dark Red
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Text(
            text = severity.name,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
