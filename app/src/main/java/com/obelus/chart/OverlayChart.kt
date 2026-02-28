package com.obelus.chart

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// OverlayChart.kt
// Gráfico pequeño flotante (Picture-in-Picture) arrastrable sobre ScanScreen.
// Arrastrable, ocultable, cierre disponible.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Modo de anclaje del overlay al liberarlo.
 */
enum class OverlayAnchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Gráfico flotante tipo Picture-in-Picture que puede arrastrarse
 * libremente sobre la pantalla y anclarse en cualquier esquina.
 *
 * @param signalId      Id de la señal a monitorear.
 * @param data          Puntos del gráfico (últimos N).
 * @param visible       Si true, el overlay es visible.
 * @param onDismiss     Callback cuando el usuario cierra el overlay.
 * @param chartType     Tipo de gráfico a dibujar (default LINE_CHART).
 * @param signalLabel   Etiqueta mostrada en el header del overlay.
 */
@Composable
fun OverlayChart(
    signalId: String,
    data: List<ChartPoint>,
    visible: Boolean = true,
    onDismiss: () -> Unit = {},
    chartType: ChartType = ChartType.LINE_CHART,
    signalLabel: String = signalId,
    lastValue: String = data.lastOrNull()?.let {
        "${"%.2f".format(it.value)} ${it.unit}"
    } ?: "—"
) {
    AnimatedVisibility(
        visible = visible,
        enter   = slideInHorizontally(tween(250)) { it } + fadeIn(tween(250)),
        exit    = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200))
    ) {
        var offsetX by remember { mutableFloatStateOf(16f) }
        var offsetY by remember { mutableFloatStateOf(200f) }

        Popup(
            properties = PopupProperties(focusable = false),
            offset     = IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
        ) {
            Box(
                modifier = Modifier
                    .width(190.dp)
                    .height(130.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE6102027))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(14.dp))
            ) {
                Column {
                    // ── Drag handle + header ──────────────────────────────────
                    Row(
                        verticalAlignment   = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectDragGestures { _, dragAmount ->
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(Icons.Default.DragHandle, null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            signalLabel, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.primary,
                            maxLines   = 1,
                            modifier   = Modifier.weight(1f)
                        )
                        // Live value badge
                        Text(
                            lastValue, fontSize = 10.sp,
                            color  = if (data.lastOrNull()?.isAlert == true)
                                Color(0xFFFFC107) else Color(0xFF80DEEA),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick  = onDismiss,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp))
                        }
                    }

                    // ── Mini chart ────────────────────────────────────────────
                    RealTimeChart(
                        data     = data,
                        type     = chartType,
                        modifier = Modifier.fillMaxSize(),
                        theme    = ChartTheme(
                            strokeWidthDp = 2f,
                            fillAlpha     = 0.15f,
                            lineColor     = Color(0xFF4FC3F7),
                            gridColor     = Color(0xFF263238),
                            labelColor    = Color(0xFF546E7A)
                        )
                    )
                }
            }
        }
    }
}

/**
 * Toggle button para mostrar/ocultar el OverlayChart desde ScanScreen.
 *
 * @param isVisible     Estado actual del overlay.
 * @param signalLabel   Señal monitorizada (mostrada en el tooltip).
 * @param onClick       Callback al pulsar.
 */
@Composable
fun OverlayChartToggleButton(
    isVisible: Boolean,
    signalLabel: String = "Señal",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick           = onClick,
        modifier          = modifier.size(44.dp),
        containerColor    = if (isVisible) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
        contentColor      = if (isVisible) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
        shape             = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.ShowChart,
            contentDescription = if (isVisible) "Ocultar gráfico" else "Mostrar gráfico $signalLabel",
            modifier = Modifier.size(20.dp)
        )
    }
}
