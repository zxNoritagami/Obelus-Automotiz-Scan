package com.obelus.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.model.DTC
import com.obelus.data.model.DTCSeverity
import com.obelus.data.repository.EnrichedDtc
import com.obelus.data.repository.SeverityLevel
import com.obelus.presentation.viewmodel.DTCViewModel

// ─────────────────────────────────────────────────────────────
// Paleta de severidad
// ─────────────────────────────────────────────────────────────
private val ColorError   = Color(0xFFEF4444)   // Rojo
private val ColorWarning = Color(0xFFF59E0B)   // Ámbar
private val ColorInfo    = Color(0xFF3B82F6)   // Azul
private val ColorGeneric = Color(0xFF6B7280)   // Gris

private val MANUFACTURERS = listOf("GENERIC", "VAG", "BMW", "TOYOTA")

// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DTCScreen(
    viewModel: DTCViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val enrichedDtcs      by viewModel.enrichedDtcs.collectAsState()
    val rawDtcs           by viewModel.dtcs.collectAsState()
    val isScanning        by viewModel.isScanning.collectAsState()
    val isEnriching       by viewModel.isEnriching.collectAsState()
    val manufacturer      by viewModel.detectedManufacturer.collectAsState()
    var showMfrPicker     by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Códigos DTC", fontWeight = FontWeight.Bold)
                        if (manufacturer != "GENERIC") {
                            Text(
                                "Base de datos: $manufacturer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    // Selector de fabricante
                    IconButton(onClick = { showMfrPicker = true }) {
                        Icon(Icons.Default.Factory, contentDescription = "Seleccionar fabricante")
                    }
                    if (enrichedDtcs.isNotEmpty() || rawDtcs.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearDTCs() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar códigos")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isScanning) {
                FloatingActionButton(onClick = { viewModel.readDTCs() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Escanear")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isScanning -> ScanningState()
                isEnriching -> EnrichingState(manufacturer)
                enrichedDtcs.isNotEmpty() -> DtcList(enrichedDtcs)
                rawDtcs.isNotEmpty() -> {
                    // Hay DTCs pero sin enriquecimiento (sin conexión a DB o no fue a Room)
                    DtcListGeneric(rawDtcs)
                }
                else -> EmptyDTCState(Modifier.align(Alignment.Center))
            }
        }
    }

    // Diálogo selector de fabricante
    if (showMfrPicker) {
        ManufacturerPickerDialog(
            current   = manufacturer,
            onSelect  = { mfr ->
                viewModel.selectManufacturer(mfr)
                showMfrPicker = false
            },
            onDismiss = { showMfrPicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Lista enriquecida
// ─────────────────────────────────────────────────────────────
@Composable
private fun DtcList(dtcs: List<EnrichedDtc>) {
    LazyColumn(
        contentPadding     = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(dtcs, key = { it.dtc.code }) { enriched ->
            EnrichedDtcCard(enriched)
        }
    }
}

@Composable
private fun EnrichedDtcCard(enriched: EnrichedDtc) {
    var expanded by remember { mutableStateOf(false) }

    val severityColor = when (enriched.severityLevel) {
        SeverityLevel.ERROR   -> ColorError
        SeverityLevel.WARNING -> ColorWarning
        SeverityLevel.INFO    -> ColorInfo
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Fila superior: código + severidad + sistema ──────────────
            Row(
                modifier            = Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Código DTC con color de severidad
                Text(
                    text       = enriched.dtc.code,
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = severityColor,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Chip sistema
                    if (enriched.manufacturerInfo != null) {
                        SystemChip(enriched.systemLabel)
                    }
                    // Chip severidad
                    SeverityChipNew(enriched.severityLevel, severityColor)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Descripción en español (o inglés si no hay ES) ───────────
            Text(
                text  = enriched.descriptionEs,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            // ── Fabricante si es específico ─────────────────────────────
            if (enriched.manufacturer != "GENERIC") {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Base: ${enriched.manufacturer} | Cat: ${enriched.dtc.category.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Posibles causas (expandibles) ────────────────────────────
            if (enriched.possibleCauses.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = if (expanded) "▲ Ocultar posibles causas" else "▼ Ver posibles causas (${enriched.possibleCauses.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter   = fadeIn(tween(200)) + expandVertically(),
                    exit    = fadeOut(tween(200)) + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Posibles causas:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        enriched.possibleCauses.forEach { cause ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "•  ",
                                    color = severityColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    cause,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Chips de sistema y severidad
// ─────────────────────────────────────────────────────────────
@Composable
private fun SystemChip(system: String) {
    val label = when(system) {
        "ENGINE"       -> "Motor"
        "TRANSMISSION" -> "Trans."
        "ABS"          -> "ABS"
        "AIRBAG"       -> "Airbag"
        "FUEL"         -> "Combustible"
        "EMISSIONS"    -> "Emisiones"
        "ELECTRICAL"   -> "Eléct."
        "BODY"         -> "Carrocería"
        "NETWORK"      -> "Red CAN"
        else           -> system
    }
    Surface(
        color  = MaterialTheme.colorScheme.secondaryContainer,
        shape  = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            style    = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SeverityChipNew(severity: SeverityLevel, color: Color) {
    val label = when (severity) {
        SeverityLevel.ERROR   -> "ERROR"
        SeverityLevel.WARNING -> "AVISO"
        SeverityLevel.INFO    -> "INFO"
    }
    Surface(
        color  = color.copy(alpha = 0.15f),
        shape  = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            label,
            style    = MaterialTheme.typography.labelSmall,
            color    = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Selector de fabricante
// ─────────────────────────────────────────────────────────────
@Composable
private fun ManufacturerPickerDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Base de datos DTC") },
        text = {
            Column {
                Text(
                    "Selecciona el fabricante para usar DTCs específicos:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                MANUFACTURERS.forEach { mfr ->
                    val label = when(mfr) {
                        "VAG"    -> "VAG (VW / Audi / Seat / Skoda)"
                        "BMW"    -> "BMW / Mini"
                        "TOYOTA" -> "Toyota / Lexus / Prius"
                        else     -> "Genérico (OBD2 estándar)"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mfr) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (mfr == current), onClick = { onSelect(mfr) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ─────────────────────────────────────────────────────────────
// Fallback: DTCs genéricos sin enriquecimiento
// ─────────────────────────────────────────────────────────────
@Composable
private fun DtcListGeneric(dtcs: List<DTC>) {
    LazyColumn(
        contentPadding     = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dtcs, key = { it.code }) { dtc ->
            DTCItemLegacy(dtc)
        }
    }
}

@Composable
private fun DTCItemLegacy(dtc: DTC) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    dtc.code,
                    style  = MaterialTheme.typography.headlineSmall,
                    color  = MaterialTheme.colorScheme.error
                )
                LegacySeverityChip(dtc.severity)
            }
            Spacer(Modifier.height(8.dp))
            Text(dtc.description, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Categoría: ${dtc.category.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegacySeverityChip(severity: DTCSeverity) {
    val color = when (severity) {
        DTCSeverity.LOW      -> ColorInfo
        DTCSeverity.MEDIUM   -> ColorWarning
        DTCSeverity.HIGH     -> ColorError
        DTCSeverity.CRITICAL -> Color(0xFF8B0000)
    }
    Surface(
        color  = color.copy(alpha = 0.2f),
        shape  = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            severity.name,
            style    = MaterialTheme.typography.labelSmall,
            color    = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Estados vacío / cargando
// ─────────────────────────────────────────────────────────────
@Composable
private fun ScanningState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Escaneando códigos de error...")
    }
}

@Composable
private fun EnrichingState(manufacturer: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = ColorInfo)
        Spacer(Modifier.height(16.dp))
        Text("Consultando base de datos $manufacturer...", color = ColorInfo)
    }
}

@Composable
fun EmptyDTCState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No se han encontrado códigos DTC.",
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pulsa el botón para escanear.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Mantener SeverityChip legacy para retrocompatibilidad
@Composable
fun SeverityChip(severity: DTCSeverity) {
    LegacySeverityChip(severity)
}
