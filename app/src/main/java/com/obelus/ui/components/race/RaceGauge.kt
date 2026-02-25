package com.obelus.ui.components.race

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.obelus.ui.theme.NeonCyan
import com.obelus.ui.theme.RaceAccent

@Composable
fun RaceGauge(
    speed: Int,
    maxSpeed: Int = 260,
    modifier: Modifier = Modifier
) {
    val progress = (speed.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
    
    // Suavizado inmersivo
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "raceSpeedAnim"
    )

    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startAngle = 135f
            val sweepAngle = 270f
            val strokeW = 60f

            // Background (Gris)
            drawArc(
                color = Color(0xFF1E1E28),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                size = Size(size.width, size.height)
            )

            // Redline Zone (ultimos 20%)
            drawArc(
                color = RaceAccent.copy(alpha = 0.3f),
                startAngle = startAngle + sweepAngle * 0.8f,
                sweepAngle = sweepAngle * 0.2f,
                useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                size = Size(size.width, size.height)
            )

            // Dynamic Fill (Gradient Cyan -> RaceAccent)
            val gradientSweep = Brush.sweepGradient(
                0.0f to NeonCyan,
                0.8f to RaceAccent,
                center = Offset(size.width / 2, size.height / 2)
            )

            drawArc(
                brush = gradientSweep,
                startAngle = startAngle,
                sweepAngle = sweepAngle * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                size = Size(size.width, size.height)
            )
        }
    }
}
