package com.obelus.presentation.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.presentation.ui.components.CircularGauge
import com.obelus.presentation.ui.components.ConnectionStatusChip
import com.obelus.presentation.ui.components.LinearGauge
import com.obelus.presentation.viewmodel.ScanStatus
import com.obelus.presentation.viewmodel.ScanViewModel

@SuppressLint("MissingPermission")
@Composable
fun ScanScreen(
    onNavigateToDtcs: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val scanState by viewModel.scanState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val readings by viewModel.currentReadings.collectAsState()
    
    // For device selection
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter
    val pairedDevices = remember { 
        adapter?.bondedDevices?.toList() ?: emptyList() 
    }
    
    var showDeviceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionStatusChip(
                isConnected = scanState == ScanStatus.CONNECTED || scanState == ScanStatus.SCANNING || scanState == ScanStatus.PAUSED,
                isScanning = scanState == ScanStatus.SCANNING
            )
            
            IconButton(onClick = onNavigateToHistory) {
                Icon(Icons.Default.History, contentDescription = "History", tint = MaterialTheme.colorScheme.primary)
            }
            


            IconButton(onClick = onNavigateToDtcs) {
                Icon(Icons.Default.Warning, contentDescription = "DTCs", tint = MaterialTheme.colorScheme.error)
            }
            
            if (scanState == ScanStatus.SCANNING) {
                Button(
                    onClick = { viewModel.stopScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DETENER")
                }
            } else if (scanState == ScanStatus.CONNECTED || scanState == ScanStatus.PAUSED) {
                 Button(
                    onClick = { viewModel.startScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (scanState == ScanStatus.PAUSED) "REANUDAR" else "INICIAR")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error Message
        errorMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = msg, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Main Content based on State
        Box(modifier = Modifier.weight(1f)) {
            when (scanState) {
                ScanStatus.IDLE, ScanStatus.DISCONNECTED, ScanStatus.ERROR -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = { showDeviceDialog = true }) {
                            Text("Conectar a Dispositivo OBD2")
                        }
                    }
                }
                ScanStatus.CONNECTING -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Conectando al dispositivo...")
                    }
                }
                ScanStatus.CONNECTED, ScanStatus.SCANNING, ScanStatus.PAUSED -> {
                    DashboardContent(readings = readings)
                }
            }
        }
    }
    
    // Device Selection Dialog
    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text("Seleccionar Dispositivo OBD2") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    if (pairedDevices.isEmpty()) {
                        item { Text("No hay dispositivos vinculados. Vincula en ajustes de Bluetooth.") }
                    } else {
                        items(pairedDevices) { device ->
                            TextButton(
                                onClick = {
                                    viewModel.connect(device.address)
                                    showDeviceDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${device.name} (${device.address})")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun DashboardContent(readings: Map<String, com.obelus.data.obd2.ObdReading>) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Grid de gauges principales (2x2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                CircularGauge(
                    value = readings["0C"]?.value ?: 0f,  // RPM
                    maxValue = 8000f,
                    unit = "rpm",
                    label = "RPM",
                    color = Color(0xFF2196F3)  // Azul
                )
            }
            item {
                CircularGauge(
                    value = readings["0D"]?.value ?: 0f,  // Speed
                    maxValue = 240f,
                    unit = "km/h",
                    label = "VELOCIDAD",
                    color = Color(0xFF4CAF50)  // Verde
                )
            }
            item {
                val temp = readings["05"]?.value ?: 0f
                CircularGauge(
                    value = temp,  // Coolant Temp
                    maxValue = 150f,
                    unit = "°C",
                    label = "TEMP MOTOR",
                    color = when {
                        temp > 100 -> Color(0xFFF44336) // Red
                        temp > 90 -> Color(0xFFFF9800)  // Orange
                        else -> Color(0xFF00BCD4)  // Cyan
                    }
                )
            }
             // Placeholder for forth gauge or voltage
            item {
                 Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                     Text("Esperando Datos...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                 }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Barras inferiores para porcentajes
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                LinearGauge(
                    value = readings["04"]?.value ?: 0f,  // Engine Load
                    maxValue = 100f,
                    unit = "%",
                    label = "Carga del Motor"
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearGauge(
                    value = readings["11"]?.value ?: 0f,  // Throttle
                    maxValue = 100f,
                    unit = "%",
                    label = "Posición del Acelerador"
                )
            }
        }
    }
}
