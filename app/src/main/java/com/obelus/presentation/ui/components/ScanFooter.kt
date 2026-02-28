package com.obelus.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// ScanFooter.kt
// Pie de la pantalla: FPS, cronómetro de sesión, botón exportar.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Barra inferior de estadísticas de la sesión de diagnóstico.
 *
 * @param framesPerSecond   Frecuencia de lectura actual (lecturas/s).
 * @param sessionElapsedMs  Milisegundos transcurridos desde el inicio de sesión.
 * @param readingCount      Total de lecturas en la sesión activa.
 * @param isScanning        Si el loop está activo (cronómetro en marcha).
 * @param onExport          Callback al pulsar el botón de exportar.
 */
@Composable
fun ScanFooter(
    framesPerSecond: Float  = 0f,
    sessionElapsedMs: Long  = 0L,
    readingCount: Int       = 0,
    isScanning: Boolean     = false,
    onExport: () -> Unit    = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── FPS ──────────────────────────────────────────────────────────
            StatChip(
                icon  = Icons.Default.Speed,
                label = "%.1f Hz".format(framesPerSecond),
                tint  = if (framesPerSecond >= 8f) Color(0xFF00C853)
                        else if (framesPerSecond >= 4f) Color(0xFFFFB300)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Cronómetro de sesión ──────────────────────────────────────────
            StatChip(
                icon  = Icons.Default.Timer,
                label = formatElapsed(sessionElapsedMs),
                tint  = MaterialTheme.colorScheme.primary
            )

            // ── Contador de lecturas ──────────────────────────────────────────
            StatChip(
                icon  = Icons.Default.Commit,
                label = "$readingCount",
                tint  = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.weight(1f))

            // ── Botón Exportar ────────────────────────────────────────────────
            OutlinedButton(
                onClick = onExport,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Exportar",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Exportar",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

/** Formatea milisegundos a "MM:SS". */
fun formatElapsed(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "%02d:%02d".format(mins, secs)
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E, name = "ScanFooter – Scanning")
@Composable
private fun PreviewFooterScanning() {
    MaterialTheme {
        ScanFooter(framesPerSecond = 9.2f, sessionElapsedMs = 127_000L, readingCount = 1143, isScanning = true)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E, name = "ScanFooter – Idle")
@Composable
private fun PreviewFooterIdle() {
    MaterialTheme { ScanFooter() }
}
