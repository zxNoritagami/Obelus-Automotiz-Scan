package com.obelus.ui.components.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GaugeArc(
    value: Float,
    minValue: Float,
    maxValue: Float,
    activeColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 30f
) {
    val range = maxValue - minValue
    val progressRaw = if (range > 0) ((value - minValue) / range).coerceIn(0f, 1f) else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressRaw,
        animationSpec = tween(durationMillis = 600),
        label = "GaugeArcAnimation"
    )

    Box(modifier = modifier.aspectRatio(1f)) {
        // Glow Effect Layer
        Canvas(modifier = Modifier.fillMaxSize().blur(8.dp)) {
            val startAngle = 140f
            val sweepAngle = 260f
            drawArc(
                color = activeColor.copy(alpha = 0.3f),
                startAngle = startAngle,
                sweepAngle = sweepAngle * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth + 10f, cap = StrokeCap.Round)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val startAngle = 140f
            val sweepAngle = 260f
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - (strokeWidth / 2)

            // 1. Background Track
            drawArc(
                color = Color(0xFF1A1A24),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Ticks (Escala)
            val tickCount = 20
            for (i in 0..tickCount) {
                val angle = startAngle + (sweepAngle / tickCount) * i
                val angleRad = Math.toRadians(angle.toDouble())
                val isMajor = i % 5 == 0
                val tickLength = if (isMajor) 15.dp.toPx() else 8.dp.toPx()
                
                val innerR = radius - strokeWidth - 5f
                val outerR = innerR - tickLength
                
                val start = Offset(
                    (center.x + innerR * cos(angleRad)).toFloat(),
                    (center.y + innerR * sin(angleRad)).toFloat()
                )
                val end = Offset(
                    (center.x + outerR * cos(angleRad)).toFloat(),
                    (center.y + outerR * sin(angleRad)).toFloat()
                )
                
                drawLine(
                    color = if (i <= tickCount * animatedProgress) activeColor.copy(alpha = 0.8f) else Color.DarkGray,
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. Main Progress with Gradient
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to activeColor.copy(alpha = 0.2f),
                    animatedProgress to activeColor,
                    1.0f to activeColor,
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // 4. Pointer head
            val pointerAngle = startAngle + sweepAngle * animatedProgress
            val pointerRad = Math.toRadians(pointerAngle.toDouble())
            val pointerPos = Offset(
                (center.x + radius * cos(pointerRad)).toFloat(),
                (center.y + radius * sin(pointerRad)).toFloat()
            )
            
            drawCircle(
                color = Color.White,
                radius = 5f,
                center = pointerPos
            )
        }
    }
}
