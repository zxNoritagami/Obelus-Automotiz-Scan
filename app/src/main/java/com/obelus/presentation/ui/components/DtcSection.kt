package com.obelus.presentation.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.data.local.entity.DtcCode

// ─────────────────────────────────────────────────────────────────────────────
// DtcSection.kt
// Sección colapsable de códigos de diagnóstico (DTCs).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sección colapsable que muestra los DTCs almacenados por el vehículo.
 *
 * @param dtcs          Lista de códigos de falla.
 * @param isLoading     Indicador de lectura en progreso.
 * @param onReadDtcs    Callback al pulsar "Leer DTCs".
 * @param onClearAll    Callback al pulsar "Borrar todos".
 * @param onClearSingle Callback al borrar un DTC específico.
 */
@Composable
fun DtcSection(
    dtcs: List<DtcCode>        = emptyList(),
    isLoading: Boolean         = false,
    onReadDtcs: () -> Unit     = {},
    onClearAll: () -> Unit     = {},
    onClearSingle: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "ChevronRotate"
    )
    val activeDtcCount = dtcs.count { it.isActive }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // ── Header colapsable ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = when {
                        activeDtcCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                        activeDtcCount <= 2 -> Color(0xFFFFB300)
                        else                -> Color(0xFFE53935)
                    },
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = buildString {
                        append("Códigos de falla")
                        if (dtcs.isNotEmpty()) append(" ($activeDtcCount activo${if (activeDtcCount != 1) "s" else ""})")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Botón Leer DTCs
                TextButton(
                    onClick = onReadDtcs,
                    enabled = !isLoading,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("Leyendo…", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Leer", fontSize = 12.sp)
                    }
                }
                // Chevron expansión
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Colapsar" else "Expandir",
                        modifier = Modifier.rotate(chevronAngle)
                    )
                }
            }

            // ── Lista expandida ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    if (dtcs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hay códigos de falla activos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        // Hasta 5 DTCs (scroll exterior del LazyColumn principal)
                        dtcs.take(5).forEach { dtc ->
                            DtcItem(dtc = dtc, onClear = { onClearSingle(dtc.code) })
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        if (dtcs.size > 5) {
                            Text(
                                text = "+${dtcs.size - 5} más…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        // Botón Borrar todos
                        if (dtcs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearAll,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Borrar todos", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DtcItem(dtc: DtcCode, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Categoría (P/C/B/U)
        Surface(
            color = dtcCategoryColor(dtc.category).copy(alpha = 0.2f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = dtc.category.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = dtcCategoryColor(dtc.category),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dtc.code,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dtc.description ?: "Descripción no disponible",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Borrar ${dtc.code}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun dtcCategoryColor(cat: Char) = when (cat.uppercaseChar()) {
    'P' -> Color(0xFFE53935)
    'C' -> Color(0xFFFF6F00)
    'B' -> Color(0xFF1565C0)
    'U' -> Color(0xFF6A1B9A)
    else -> Color.Gray
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "DtcSection – With DTCs")
@Composable
private fun PreviewDtcSectionWithDtcs() {
    MaterialTheme {
        val sample = listOf(
            DtcCode("P0133", "O2 Sensor Circuit Slow Response", 'P', true, false, false, null, null, null, null),
            DtcCode("P0420", "Catalyst System Efficiency Below Threshold", 'P', true, false, false, null, null, null, null),
            DtcCode("U0001", "High Speed CAN Communication Bus", 'U', false, true, false, null, null, null, null)
        )
        DtcSection(dtcs = sample)
    }
}
