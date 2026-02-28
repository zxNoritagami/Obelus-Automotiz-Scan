package com.obelus.freezeframe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.domain.model.FreezeFrameData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// FreezeFrameCard.kt
// Componente compacto para mostrar en listas de DTCs.
// ─────────────────────────────────────────────────────────────────────────────

private val DateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

/**
 * Tarjeta compacta de freeze frame para listas de DTCs.
 *
 * @param dtcCode       Código DTC.
 * @param freezeFrame   Datos del último freeze frame (null = no capturado aún).
 * @param hasFreezeFrame Si true muestra el icono de cámara/nieve.
 * @param analysisResult Resultado de análisis (muestra hallazgos críticos).
 * @param onClick       Callback al pulsar.
 */
@Composable
fun FreezeFrameCard(
    dtcCode: String,
    freezeFrame: FreezeFrameData?,
    hasFreezeFrame: Boolean = freezeFrame != null,
    analysisResult: AnalysisResult? = null,
    onClick: () -> Unit = {}
) {
    val criticalFindings = analysisResult?.findings?.filter { it.severity == FindingSeverity.CRITICAL }
    val hasCritical = !criticalFindings.isNullOrEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape    = RoundedCornerShape(12.dp),
        border   = if (hasCritical) BorderStroke(1.dp, Color(0xFFF44336).copy(alpha = 0.7f)) else null,
        colors   = CardDefaults.cardColors(
            containerColor = if (hasCritical) Color(0x1AF44336)
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // ── Freeze icon ───────────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasFreezeFrame) Color(0xFF0288D1).copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surface
                    )
            ) {
                Icon(
                    imageVector   = if (hasCritical) Icons.Default.Warning else Icons.Default.AcUnit,
                    contentDescription = "Freeze Frame",
                    tint          = when {
                        hasCritical    -> Color(0xFFF44336)
                        hasFreezeFrame -> Color(0xFF29B6F6)
                        else           -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // ── Text column ───────────────────────────────────────────────────
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dtcCode,
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = if (hasCritical) Color(0xFFF44336)
                                     else MaterialTheme.colorScheme.onSurface
                    )
                    if (hasFreezeFrame) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0288D1).copy(alpha = 0.18f)
                        ) {
                            Text(
                                "FREEZE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color    = Color(0xFF29B6F6),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Condition summary or first finding
                val subtitle = when {
                    !criticalFindings.isNullOrEmpty() -> criticalFindings.first().title
                    freezeFrame != null               -> freezeFrame.conditionSummary()
                    else                              -> "Sin freeze frame"
                }
                Text(
                    subtitle,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ── Date ─────────────────────────────────────────────────────────
            freezeFrame?.let {
                Text(
                    DateFmt.format(Date(it.timestamp)),
                    style  = MaterialTheme.typography.labelSmall,
                    color  = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )
            }
        }
    }
}
