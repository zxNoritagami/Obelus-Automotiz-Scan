package com.obelus.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.obelus.ui.theme.NeonCyan

/**
 * A professional real-time waveform visualizer for sensors.
 */
@Composable
fun DiagnosticScope(
    dataPoints: List<Float>,
    maxValue: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = NeonCyan
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Color(0xFF050505))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val gridSpacing = 30.dp.toPx()

            // Draw technical grid
            for (x in 0..width.toInt() step gridSpacing.toInt()) {
                drawLine(Color.White.copy(alpha = 0.05f), Offset(x.toFloat(), 0f), Offset(x.toFloat(), height))
            }
            for (y in 0..height.toInt() step gridSpacing.toInt()) {
                drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y.toFloat()), Offset(width, y.toFloat()))
            }

            if (dataPoints.size < 2) return@Canvas

            val path = Path()
            val xStep = width / (dataPoints.size - 1)
            
            dataPoints.forEachIndexed { index, value ->
                // Normalize value to height
                val normalizedY = height - ((value / maxValue) * height).coerceIn(0f, height)
                val x = index * xStep
                
                if (index == 0) path.moveTo(x, normalizedY)
                else path.lineTo(x, normalizedY)
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Draw gradient area under path
            // (Optional for more "Pro" look)
        }
    }
}
