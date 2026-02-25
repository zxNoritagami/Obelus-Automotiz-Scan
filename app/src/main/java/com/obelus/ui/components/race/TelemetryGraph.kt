package com.obelus.ui.components.race

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
import com.obelus.ui.theme.NeonCyan

@Composable
fun TelemetryGraph(
    series: List<Pair<Long, Int>>,
    maxSpeed: Int,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) return

    val lineColor = NeonCyan
    val gridColor = Color.White.copy(alpha = 0.05f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val padL = 40f
        val padB = 32f
        val chartW = w - padL
        val chartH = h - padB

        val maxT = series.last().first.coerceAtLeast(1L).toFloat()
        val maxV = maxSpeed.coerceAtLeast(series.maxOf { it.second }).toFloat()

        // Dibujar Grid Inferior (Velocidad Dividida en 4 partes)
        for (i in 0..4) {
            val y = padB + chartH * (1f - i / 4f)
            drawLine(gridColor, Offset(padL, y), Offset(w, y), strokeWidth = 1f)
        }

        // Trazar Linea de Velocidad y Area debajo
        val path = Path()
        val fillPath = Path()
        
        fillPath.moveTo(padL, padB + chartH) // Inicio inferior izquierdo

        series.forEachIndexed { idx, (t, v) ->
            val x = padL + (t.toFloat() / maxT) * chartW
            val y = padB + chartH * (1f - v.toFloat() / maxV)
            
            if (idx == 0) {
                path.moveTo(x, y)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        
        // Terminar el path de area debajo (hasta abajo derecha y cerrar)
        series.lastOrNull()?.let { (t, _) ->
            val endX = padL + (t.toFloat() / maxT) * chartW
            fillPath.lineTo(endX, padB + chartH)
            fillPath.close()
        }

        // Area sombreada (Gradiente vertical alfa)
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent),
                startY = padB,
                endY = padB + chartH
            )
        )

        // Linea principal (Curva Velocidad)
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
    }
}
