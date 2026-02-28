package com.obelus.presentation.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// ScanControls.kt
// Controles de inicio/parada de escaneo y selector de modo de lectura.
// ─────────────────────────────────────────────────────────────────────────────

/** Modo de lectura de señales. */
enum class ScanMode {
    /** PIDs estándar OBD2 (Modo 01). */
    GENERIC_PIDS,
    /** Señales del archivo DBC cargado. */
    DBC_LOADED,
    /** Selección manual de señales. */
    CUSTOM
}

/**
 * Fila de controles superior del escaneo.
 *
 * @param isScanning        Estado actual del loop de lectura.
 * @param isConnected       Si hay conexión BT activa.
 * @param selectedMode      Modo de señales activo.
 * @param hasDbcLoaded      Si hay un DBC cargado disponible.
 * @param customSignalCount Número de señales en selección personalizada.
 * @param onStart           Callback al pulsar Iniciar.
 * @param onStop            Callback al pulsar Detener.
 * @param onModeChange      Callback al cambiar de modo.
 * @param onAddSignal       Callback al pulsar el chip "+" (modo Custom).
 */
@Composable
fun ScanControls(
    isScanning: Boolean       = false,
    isConnected: Boolean      = true,
    selectedMode: ScanMode    = ScanMode.GENERIC_PIDS,
    hasDbcLoaded: Boolean     = false,
    customSignalCount: Int    = 0,
    onStart: () -> Unit       = {},
    onStop: () -> Unit        = {},
    onModeChange: (ScanMode) -> Unit = {},
    onAddSignal: () -> Unit   = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Botón principal Iniciar/Detener ───────────────────────────────────
        AnimatedContent(
            targetState = isScanning,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.92f))
            },
            label = "ScanButton"
        ) { scanning ->
            Button(
                onClick = if (scanning) onStop else onStart,
                enabled = isConnected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (scanning) MaterialTheme.colorScheme.error
                                     else Color(0xFF00C853),
                    contentColor   = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp, pressedElevation = 2.dp
                )
            ) {
                Icon(
                    imageVector = if (scanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (scanning) "DETENER ESCANEO" else "INICIAR ESCANEO",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Selector de modo ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScanMode.values().forEach { mode ->
                val enabled = when (mode) {
                    ScanMode.DBC_LOADED -> hasDbcLoaded
                    else                -> true
                }
                val label = when (mode) {
                    ScanMode.GENERIC_PIDS -> "PIDs Genéricos"
                    ScanMode.DBC_LOADED   -> "DBC Cargado"
                    ScanMode.CUSTOM       -> if (customSignalCount > 0) "Custom ($customSignalCount)" else "Personalizado"
                }
                FilterChip(
                    selected = selectedMode == mode,
                    onClick  = { if (enabled) onModeChange(mode) },
                    enabled  = enabled,
                    label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = if (selectedMode == mode) ({
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    }) else null,
                    shape = RoundedCornerShape(50)
                )
            }

            // Chip "+" para agregar señal (sólo en modo Custom)
            if (selectedMode == ScanMode.CUSTOM) {
                InputChip(
                    selected = false,
                    onClick  = onAddSignal,
                    label    = { Text("+ Señal") },
                    leadingIcon = {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(50)
                )
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ScanControls – Idle")
@Composable
private fun PreviewControlsIdle() {
    MaterialTheme {
        ScanControls(isScanning = false, isConnected = true, hasDbcLoaded = true)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ScanControls – Scanning Custom")
@Composable
private fun PreviewControlsScanning() {
    MaterialTheme {
        ScanControls(
            isScanning        = true,
            isConnected       = true,
            selectedMode      = ScanMode.CUSTOM,
            customSignalCount = 4
        )
    }
}
