package com.obelus.ui.components.logs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Gráfico parametrizado para múltiples señales sobrepuestas (Logs).
 */
@Composable
fun SignalChart(
    signals: List<Pair<List<Pair<Long, Float>>, Color>>, // Lista de señales: (Datos(x,y), Color)
    modifier: Modifier = Modifier
) {
    if (signals.isEmpty()) return

    val gridColor = Color.White.copy(alpha = 0.05f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val padL = 40f
        val padB = 40f
        val chartW = w - padL
        val chartH = h - padB

        // Encontrar maxT y maxV global entre todas las señales para escalar la grilla uniformemente
        var maxT = 1L
        var minT = Long.MAX_VALUE
        var maxV = Float.MIN_VALUE
        var minV = Float.MAX_VALUE

        signals.forEach { (data, _) ->
            if (data.isNotEmpty()) {
                val tMax = data.maxOf { it.first }
                val tMin = data.minOf { it.first }
                val vMax = data.maxOf { it.second }
                val vMin = data.minOf { it.second }
                if (tMax > maxT) maxT = tMax
                if (tMin < minT) minT = tMin
                if (vMax > maxV) maxV = vMax
                if (vMin < minV) minV = vMin
            }
        }
        
        if (minT == Long.MAX_VALUE) minT = 0L // Seguridad
        val rangeT = maxOf(1L, maxT - minT).toFloat()
        val rangeV = maxOf(0.1f, maxV - minV)

        // Dibujar Grid (Líneas horizontales y verticales suaves)
        for (i in 0..4) {
            val y = padB + chartH * (1f - i / 4f)
            drawLine(gridColor, Offset(padL, y), Offset(w, y), strokeWidth = 1f)
            val x = padL + chartW * (i / 4f)
            drawLine(gridColor, Offset(x, padB), Offset(x, h), strokeWidth = 1f)
        }

        // Trazar señales
        signals.forEach { (data, color) ->
            if (data.isEmpty()) return@forEach

            val path = Path()
            val fillPath = Path()
            fillPath.moveTo(padL, padB + chartH) // Base baja echa para el Brush

            data.forEachIndexed { idx, (t, v) ->
                val x = padL + ((t - minT).toFloat() / rangeT) * chartW
                // Normalizar Y. Si v == minV -> y será abajo (padB + chartH). Si v == maxV -> y será arriba (padB).
                val normalizedY = (v - minV) / rangeV
                val y = padB + chartH * (1f - normalizedY)

                if (idx == 0) {
                    path.moveTo(x, y)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            // Terminar envolvente del Brush
            data.lastOrNull()?.let { (t, _) ->
                val xEnd = padL + ((t - minT).toFloat() / rangeT) * chartW
                fillPath.lineTo(xEnd, padB + chartH)
                fillPath.close()
            }

            // Área Gradient (Sutil translúcida bajo la curva)
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.2f), Color.Transparent),
                    startY = padB,
                    endY = padB + chartH
                )
            )

            // Línea Principal Sólida
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }
}
