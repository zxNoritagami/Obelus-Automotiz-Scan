package com.obelus.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.local.model.Status
import com.obelus.presentation.ui.components.ReadingItem
import com.obelus.presentation.viewmodel.ScanState
import com.obelus.presentation.viewmodel.ScanViewModel

@Composable
fun ScanScreen(
    onNavigateToDtcs: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val scanState by viewModel.scanState.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Obelus Scanner") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Status Indicator
            StatusHeader(scanState, errorMessage)

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        if (scanState == ScanState.SCANNING) {
                            viewModel.stopScan()
                        } else {
                            viewModel.startScan("Session_${System.currentTimeMillis()}")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (scanState == ScanState.SCANNING) Color.Red else Color.Green
                    )
                ) {
                    Text(text = if (scanState == ScanState.SCANNING) "STOP SCAN" else "START SCAN")
                }

                Button(
                    onClick = onNavigateToDtcs,
                    colors = ButtonDefaults.buttonColors(
                         containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("VIEW DTCs")
                }
            }

            // Readings List
            if (readings.isEmpty() && scanState == ScanState.SCANNING) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(readings) { reading ->
                        // Mock status logic for visualization
                        val status = if (reading.decodedValue > 100) Status.HIGH else Status.NORMAL
                        ReadingItem(
                            signalName = "Signal ${reading.signalId}", // In real app, resolve name
                            value = String.format("%.2f", reading.decodedValue),
                            unit = "N/A", // In real app, resolve unit
                            status = status
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusHeader(state: ScanState, error: String?) {
    val (color, text) = when (state) {
        ScanState.IDLE -> Color.Gray to "IDLE"
        ScanState.SCANNING -> Color.Green to "SCANNING..."
        ScanState.PAUSED -> Color.Yellow to "PAUSED"
        ScanState.ERROR -> Color.Red to (error ?: "ERROR")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.2f))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (state == ScanState.ERROR) Color.Red else MaterialTheme.colorScheme.onSurface
        )
    }
}
