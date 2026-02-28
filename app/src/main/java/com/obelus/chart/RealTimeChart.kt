package com.obelus.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// RealTimeChart.kt
// Componente Compose de gráfico en tiempo real — sin dependencias externas.
// Soporta LINE, BAR, GAUGE, SCOPE, COMPARISON.
// Pinch-to-zoom, pan horizontal, doble tap para reset.
// ─────────────────────────────────────────────────────────────────────────────

/** Config de tema del gráfico. */
data class ChartTheme(
    val lineColor:     Color = Color(0xFF4FC3F7),
    val alertColor:    Color = Color(0xFFFFC107),
    val criticalColor: Color = Color(0xFFF44336),
    val gridColor:     Color = Color(0xFF37474F),
    val labelColor:    Color = Color(0xFF90A4AE),
    val fillAlpha:     Float = 0.18f,
    val strokeWidthDp: Float = 2.5f
)

/**
 * Gráfico universal en tiempo real.
 *
 * @param data       Puntos a renderizar (ya downsampled si corresponde).
 * @param type       Tipo de gráfico a dibujar.
 * @param modifier   Modificador Compose.
 * @param fixedYMin  Si no null, eje Y mínimo fijo (no auto-escala).
 * @param fixedYMax  Si no null, eje Y máximo fijo.
 * @param unit       Unidad de medida (mostrada en etiqueta Y).
 * @param theme      Colores y estilos del gráfico.
 * @param extraSeries Señales adicionales para COMPARISON_CHART.
 */
@Composable
fun RealTimeChart(
    data: List<ChartPoint>,
    type: ChartType = ChartType.LINE_CHART,
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
    fixedYMin: Float? = null,
    fixedYMax: Float? = null,
    unit: String = "",
    theme: ChartTheme = ChartTheme(),
    extraSeries: Map<String, List<ChartPoint>> = emptyMap()
) {
    if (data.isEmpty() && extraSeries.isEmpty()) {
        EmptyChartPlaceholder(modifier)
        return
    }

    // ── Zoom / Pan state ─────────────────────────────────────────────────────
    var scale     by remember { mutableFloatStateOf(1f) }
    var offsetX   by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale   = (scale * zoomChange).coerceIn(0.5f, 8f)
        offsetX += panChange.x
    }

    val textMeasurer = rememberTextMeasurer()

    when (type) {
        ChartType.LINE_CHART,
        ChartType.COMPARISON_CHART -> LineOrComparisonChart(
            data, extraSeries, modifier, scale, offsetX, transformState,
            fixedYMin, fixedYMax, unit, theme, textMeasurer,
            onDoubleReset = { scale = 1f; offsetX = 0f }
        )
        ChartType.BAR_CHART   -> BarChart(data, modifier, fixedYMin, fixedYMax, unit, theme, textMeasurer)
        ChartType.GAUGE_CHART -> GaugeChart(data.lastOrNull()?.value ?: 0f, fixedYMin ?: 0f, fixedYMax ?: 8000f, unit, theme, modifier)
        ChartType.SCOPE_CHART -> ScopeChart(data, modifier, theme, textMeasurer)
    }
}

// ── Line / Comparison ─────────────────────────────────────────────────────────
@Composable
private fun LineOrComparisonChart(
    data: List<ChartPoint>,
    extraSeries: Map<String, List<ChartPoint>>,
    modifier: Modifier,
    scale: Float,
    offsetX: Float,
    transformState: androidx.compose.foundation.gestures.TransformableState,
    fixedYMin: Float?,
    fixedYMax: Float?,
    unit: String,
    theme: ChartTheme,
    textMeasurer: TextMeasurer,
    onDoubleReset: () -> Unit
) {
    val comparisonColors = listOf(
        Color(0xFF4FC3F7), Color(0xFFA5D6A7), Color(0xFFFFCC80),
        Color(0xFFCE93D8), Color(0xFFF48FB1)
    )
    val allSeries = buildMap {
        put(data.firstOrNull()?.signalId ?: "main", data)
        putAll(extraSeries)
    }

    Canvas(
        modifier = modifier
            .transformable(state = transformState)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleReset() })
            }
    ) {
        val w = size.width; val h = size.height
        val padL = 48f; val padR = 16f; val padT = 12f; val padB = 28f
        val chartW = (w - padL - padR) * scale
        val chartH = h - padT - padB

        // Auto Y range
        val allVals = allSeries.values.flatten().map { it.value }
        val yMin = fixedYMin ?: (allVals.minOrNull() ?: 0f)
        val yMax = fixedYMax ?: (allVals.maxOrNull()?.let { if (it == yMin) yMin + 1f else it } ?: 1f)
        val yRange = yMax - yMin

        // Grid lines
        drawGridAndAxes(this, padL, padT, padB, padR, w, h, yMin, yMax, theme, unit, textMeasurer)

        // Clip to chart area
        clipRect(padL, padT, w - padR, h - padB) {
            allSeries.values.forEachIndexed { idx, series ->
                if (series.size < 2) return@forEachIndexed
                val color = comparisonColors[idx % comparisonColors.size]
                val tMin  = series.first().timestamp.toFloat()
                val tMax  = series.last().timestamp.toFloat()
                val tRange = (tMax - tMin).coerceAtLeast(1f)

                fun toX(ts: Long) = padL + ((ts - tMin) / tRange) * chartW + offsetX
                fun toY(v: Float) = padT + chartH - ((v - yMin) / yRange) * chartH

                // Fill under curve
                val fillPath = Path()
                series.forEachIndexed { i, point ->
                    val px = toX(point.timestamp); val py = toY(point.value)
                    if (i == 0) fillPath.moveTo(px, py) else fillPath.lineTo(px, py)
                }
                fillPath.lineTo(toX(series.last().timestamp), padT + chartH)
                fillPath.lineTo(toX(series.first().timestamp), padT + chartH)
                fillPath.close()
                drawPath(fillPath, color.copy(alpha = theme.fillAlpha))

                // Stroke
                val linePath = Path()
                series.forEachIndexed { i, point ->
                    val px = toX(point.timestamp); val py = toY(point.value)
                    if (i == 0) linePath.moveTo(px, py)
                    else        linePath.lineTo(px, py)
                }
                drawPath(linePath, color, style = Stroke(width = theme.strokeWidthDp.dp.toPx(),
                    cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Alert dots
                series.filter { it.isAlert }.forEach { pt ->
                    drawCircle(theme.alertColor, 5.dp.toPx(), Offset(toX(pt.timestamp), toY(pt.value)))
                }
            }
        }
    }
}

// ── Bar chart ──────────────────────────────────────────────────────────────────
@Composable
private fun BarChart(
    data: List<ChartPoint>, modifier: Modifier,
    fixedYMin: Float?, fixedYMax: Float?, unit: String,
    theme: ChartTheme, textMeasurer: TextMeasurer
) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val padL = 48f; val padB = 28f; val padT = 12f; val padR = 16f
        val chartH = h - padT - padB
        val yMin  = fixedYMin ?: 0f
        val yMax  = fixedYMax ?: (data.maxOfOrNull { it.value }?.let { if (it == yMin) yMin + 1f else it } ?: 1f)
        val yRange = yMax - yMin

        drawGridAndAxes(this, padL, padT, padB, padR, w, h, yMin, yMax, theme, unit, textMeasurer)

        if (data.isEmpty()) return@Canvas
        val barW = ((w - padL - padR) / data.size) * 0.72f
        val gap  = ((w - padL - padR) / data.size) * 0.28f / 2

        clipRect(padL, padT, w - padR, h - padB) {
            data.forEachIndexed { i, pt ->
                val left    = padL + i * ((w - padL - padR) / data.size) + gap
                val barH    = ((pt.value - yMin) / yRange) * chartH
                val top     = padT + chartH - barH
                val color   = if (pt.isAlert) theme.alertColor else theme.lineColor
                drawRect(color, Offset(left, top), Size(barW, barH))
            }
        }
    }
}

// ── Gauge ─────────────────────────────────────────────────────────────────────
@Composable
private fun GaugeChart(
    value: Float, yMin: Float, yMax: Float,
    unit: String, theme: ChartTheme, modifier: Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = value, animationSpec = tween(150), label = "gauge"
    )
    Canvas(modifier = modifier) {
        val cx = size.width / 2; val cy = size.height * 0.68f
        val radius = minOf(cx, cy) * 0.82f
        val startAngle = 150f; val sweep = 240f

        // Track
        drawArc(theme.gridColor, startAngle, sweep, false, Offset(cx - radius, cy - radius),
            Size(radius * 2, radius * 2), style = Stroke(radius * 0.18f, cap = StrokeCap.Round))

        // Value arc
        val fraction = ((animatedValue - yMin) / (yMax - yMin)).coerceIn(0f, 1f)
        val arcColor = when {
            fraction > 0.85f -> theme.criticalColor
            fraction > 0.70f -> theme.alertColor
            else             -> theme.lineColor
        }
        drawArc(arcColor, startAngle, sweep * fraction, false,
            Offset(cx - radius, cy - radius), Size(radius * 2, radius * 2),
            style = Stroke(radius * 0.18f, cap = StrokeCap.Round))

        // Needle tip dot
        val needleAngle = Math.toRadians((startAngle + sweep * fraction).toDouble())
        val nx = cx + (radius * 0.68f) * cos(needleAngle).toFloat()
        val ny = cy + (radius * 0.68f) * sin(needleAngle).toFloat()
        drawCircle(arcColor, 8f, Offset(nx, ny))
    }
}

// ── Scope (O2 oscilloscope XY) ────────────────────────────────────────────────
@Composable
private fun ScopeChart(
    data: List<ChartPoint>, modifier: Modifier,
    theme: ChartTheme, textMeasurer: TextMeasurer
) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val w = size.width; val h = size.height
        val padL = 36f; val padB = 24f; val padT = 8f; val padR = 8f
        val chartW = w - padL - padR; val chartH = h - padT - padB
        val yMin = data.minOf { it.value }; val yMax = data.maxOf { it.value }
        val yRange = (yMax - yMin).coerceAtLeast(0.1f)
        val tMin = data.first().timestamp.toFloat(); val tMax = data.last().timestamp.toFloat()
        val tRange = (tMax - tMin).coerceAtLeast(1f)

        // Phosphor-style fading trail
        val size = data.size
        data.zipWithNext().forEachIndexed { idx, (a, b) ->
            val alpha = (idx.toFloat() / size) * 0.9f + 0.1f
            val ax = padL + ((a.timestamp - tMin) / tRange) * chartW
            val ay = padT + chartH - ((a.value - yMin) / yRange) * chartH
            val bx = padL + ((b.timestamp - tMin) / tRange) * chartW
            val by = padT + chartH - ((b.value - yMin) / yRange) * chartH
            drawLine(Color(0xFF00E5FF).copy(alpha = alpha), Offset(ax, ay), Offset(bx, by),
                strokeWidth = 2.5f)
        }

        // Zero line (lambda = 0.45V threshold)
        val lambdaY = padT + chartH - ((0.45f - yMin) / yRange) * chartH
        drawLine(theme.gridColor, Offset(padL, lambdaY), Offset(w - padR, lambdaY),
            strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────
private fun drawGridAndAxes(
    scope: DrawScope, padL: Float, padT: Float, padB: Float, padR: Float,
    w: Float, h: Float, yMin: Float, yMax: Float,
    theme: ChartTheme, unit: String, textMeasurer: TextMeasurer
) = scope.run {
    val chartH = h - padT - padB
    val gridLines = 4
    val yStep = (yMax - yMin) / gridLines
    val textStyle = TextStyle(fontSize = 9.sp, color = theme.labelColor, fontWeight = FontWeight.Light)

    for (i in 0..gridLines) {
        val y = padT + chartH - (i.toFloat() / gridLines) * chartH
        drawLine(theme.gridColor, Offset(padL, y), Offset(w - padR, y), strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        val label = "%.1f".format(yMin + i * yStep)
        val measured = textMeasurer.measure(label, textStyle)
        drawText(measured, topLeft = Offset(2f, y - measured.size.height / 2f))
    }

    // Unit label
    if (unit.isNotEmpty()) {
        val unitMeasured = textMeasurer.measure(unit, textStyle)
        drawText(unitMeasured, topLeft = Offset(2f, padT))
    }
}

@Composable
private fun EmptyChartPlaceholder(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
        Text("Sin datos", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline)
    }
}
