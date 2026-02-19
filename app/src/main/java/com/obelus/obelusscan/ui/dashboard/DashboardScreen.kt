package com.obelus.obelusscan.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.domain.model.ObdPid

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    // Collecting StateFlows
    val rpm by viewModel.rpm.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val coolantTemp by viewModel.coolantTemp.collectAsState()
    val engineLoad by viewModel.engineLoad.collectAsState()
    val throttlePos by viewModel.throttlePos.collectAsState()
    val mafRate by viewModel.mafRate.collectAsState()
    val intakePressure by viewModel.intakePressure.collectAsState()
    val barometricPressure by viewModel.barometricPressure.collectAsState()
    val fuelLevel by viewModel.fuelLevel.collectAsState()
    val ambientTemp by viewModel.ambientTemp.collectAsState()

    // Start scanning on composition
    LaunchedEffect(Unit) {
        viewModel.startScanning()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Dashboard en Tiempo Real",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            // Placeholder for real connection state (assuming connected for dashboard view)
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Bluetooth Connected",
                tint = Color(0xFF4CAF50) // Green
            )
        }

        // Grid of Gauges
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Performance (Red/Orange)
            item { DashboardCard(ObdPid.RPM, rpm, Color(0xFFF44336)) }     // Red
            item { DashboardCard(ObdPid.SPEED, speed, Color(0xFFFF9800)) } // Orange
            item { DashboardCard(ObdPid.THROTTLE_POS, throttlePos, Color(0xFFFF5722)) } // Deep Orange

            // Temperatures (Blue)
            item { DashboardCard(ObdPid.COOLANT_TEMP, coolantTemp, Color(0xFF2196F3)) } // Blue
            item { DashboardCard(ObdPid.AMBIENT_TEMP, ambientTemp, Color(0xFF03A9F4)) } // Light Blue

            // Pressures (Green)
            item { DashboardCard(ObdPid.INTAKE_PRESSURE, intakePressure, Color(0xFF4CAF50)) } // Green
            item { DashboardCard(ObdPid.BAROMETRIC_PRESSURE, barometricPressure, Color(0xFF8BC34A)) } // Light Green

            // Levels (Yellow)
            item { DashboardCard(ObdPid.FUEL_LEVEL, fuelLevel, Color(0xFFFFC107)) } // Amber
            item { DashboardCard(ObdPid.ENGINE_LOAD, engineLoad, Color(0xFFFFEB3B)) } // Yellow

            // Consumption (Purple)
            item { DashboardCard(ObdPid.MAF_RATE, mafRate, Color(0xFF9C27B0)) } // Purple
        }
    }
}

@Composable
fun DashboardCard(
    pid: ObdPid,
    value: Float,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = pid.description,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // Value + Unit
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format("%.1f", value),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = pid.unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Progress Bar
            // Calculate progress 0.0 - 1.0 based on min/max
            val range = pid.maxValue - pid.minValue
            val progressValue = if (range > 0) {
                ((value - pid.minValue) / range).coerceIn(0f, 1f)
            } else {
                0f
            }

            LinearProgressIndicator(
                progress = progressValue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}
