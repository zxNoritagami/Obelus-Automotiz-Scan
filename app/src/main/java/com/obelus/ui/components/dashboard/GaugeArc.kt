package com.obelus.ui.components.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun GaugeArc(
    value: Float,
    minValue: Float,
    maxValue: Float,
    activeColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 40f
) {
    // Normalizar valor a %
    val range = maxValue - minValue
    val progressRaw = if (range > 0) ((value - minValue) / range).coerceIn(0f, 1f) else 0f

    // Animar la barra (suave counting effect)
    val animatedProgress by animateFloatAsState(
        targetValue = progressRaw,
        animationSpec = tween(durationMillis = 800),
        label = "GaugeArcAnimation"
    )

    Box(
        modifier = modifier.aspectRatio(1f) // Cuadrado perfecto para el circulo
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startAngle = 135f
            val sweepAngle = 270f
            
            // Background track (Gris oscuro)
            drawArc(
                color = Color(0xFF2A2A35),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width, size.height),
                topLeft = Offset.Zero
            )

            // Foreground progress (Color Dinámico)
            drawArc(
                color = activeColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width, size.height),
                topLeft = Offset.Zero
            )
        }
    }
}
