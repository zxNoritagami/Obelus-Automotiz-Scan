package com.obelus.manufacturer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obelus.presentation.viewmodel.ScanViewModel

// ─────────────────────────────────────────────────────────────────────────────
// ManufacturerDataScreen.kt
// Pantalla de lectura de datos específicos de fabricante (Modo 21/22).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ManufacturerDataScreen(
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val mfrState          by viewModel.manufacturerState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Toolbar ───────────────────────────────────────────────────────────
        ManufacturerTopBar(mfrState)

        // ── VIN / Selector de vehículo ────────────────────────────────────────
        VehicleSelectorCard(
            mfrState    = mfrState,
            onVinChange = { viewModel.loadManufacturerDataForVin(it) },
            onScanAll   = { viewModel.startManufacturerScan() },
            onStop      = { viewModel.stopManufacturerScan() }
        )

        // ── Resultados ────────────────────────────────────────────────────────
        AnimatedContent(
            targetState = mfrState.readings.isEmpty() && mfrState.availableData.isEmpty(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "mfr_content"
        ) { isEmpty ->
            if (isEmpty) {
                EmptyStateBox()
            } else {
                ReadingsList(mfrState)
            }
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────
@Composable
private fun ManufacturerTopBar(state: ManufacturerState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Datos de Fabricante",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Modo 21/22 — PIDs OEM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (state.isScanning) {
            CircularProgressIndicator(
                modifier  = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        }
    }
    HorizontalDivider()
}

// ── Vehicle Selector ──────────────────────────────────────────────────────────
@Composable
private fun VehicleSelectorCard(
    mfrState:    ManufacturerState,
    onVinChange: (String) -> Unit,
    onScanAll:   () -> Unit,
    onStop:      () -> Unit
) {
    var vinText by remember { mutableStateOf(mfrState.lastVin) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Fabricante detectado
            if (mfrState.detectedManufacturer != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Fabricante: ${mfrState.detectedManufacturer.displayName}",
                        style     = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    mfrState.detectedYear?.let {
                        Text("(${it})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
                Text(
                    "${mfrState.availableData.size} PIDs disponibles en la base de datos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // VIN input
            OutlinedTextField(
                value         = vinText,
                onValueChange = { vinText = it.uppercase().take(17) },
                label         = { Text("VIN (opcional)") },
                placeholder   = { Text("JT2BF22K1W0120345") },
                singleLine    = true,
                trailingIcon  = {
                    if (vinText.isNotBlank()) {
                        IconButton(onClick = { onVinChange(vinText) }) {
                            Icon(Icons.Default.Search, "Buscar por VIN")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(8.dp)
            )

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = if (mfrState.isScanning) onStop else onScanAll,
                    modifier = Modifier.weight(1f),
                    colors   = if (mfrState.isScanning)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.buttonColors(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        if (mfrState.isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (mfrState.isScanning) "Detener" else "Scan completo")
                }
            }
        }
    }
}

// ── Readings list ─────────────────────────────────────────────────────────────
@Composable
private fun ReadingsList(state: ManufacturerState) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Group by category (manufacturer as proxy)
        val grouped = (state.readings.keys + state.availableData.map { it.dataId })
            .distinct()
            .mapNotNull { id ->
                state.readings[id] ?: state.availableData.firstOrNull { it.dataId == id }
                    ?.let { ManufacturerReading.NotSupported(it, "No escaneado aún") }
            }
            .groupBy { reading ->
                when (reading) {
                    is ManufacturerReading.Value        -> reading.data.manufacturer
                    is ManufacturerReading.NotSupported -> reading.data.manufacturer
                    is ManufacturerReading.Error        -> reading.data.manufacturer
                }
            }

        grouped.forEach { (mfr, readings) ->
            item {
                Text(
                    "  ${mfr.displayName.uppercase()}",
                    style  = MaterialTheme.typography.labelMedium,
                    color  = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            items(readings) { reading ->
                ReadingCard(reading)
            }
        }
    }
}

// ── Single reading card ───────────────────────────────────────────────────────
@Composable
private fun ReadingCard(reading: ManufacturerReading) {
    val (bgColor, statusDot, statusLabel) = when (reading) {
        is ManufacturerReading.Value -> Triple(
            if (reading.isNormal) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.errorContainer,
            if (reading.isNormal) Color(0xFF4CAF50) else Color(0xFFF44336),
            if (reading.isNormal) "OK" else "FUERA DE RANGO"
        )
        is ManufacturerReading.NotSupported -> Triple(
            MaterialTheme.colorScheme.surface, Color(0xFF9E9E9E), "No responde"
        )
        is ManufacturerReading.Error -> Triple(
            MaterialTheme.colorScheme.errorContainer, Color(0xFFF44336), "Error"
        )
    }

    val dataObj = when (reading) {
        is ManufacturerReading.Value        -> reading.data
        is ManufacturerReading.NotSupported -> reading.data
        is ManufacturerReading.Error        -> reading.data
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusDot)
            )
            Spacer(Modifier.width(10.dp))

            // Name + description
            Column(Modifier.weight(1f)) {
                Text(
                    dataObj.description,
                    style    = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "PID ${dataObj.mode}/${dataObj.pid} · ${statusLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.width(8.dp))

            // Value
            when (reading) {
                is ManufacturerReading.Value -> Text(
                    reading.displayString,
                    style      = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp
                    ),
                    color = if (reading.isNormal) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
                else -> Text(
                    "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyStateBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint     = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Ingresa un VIN o conecta al vehículo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                "y pulsa \"Scan completo\" para leer los datos OEM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
