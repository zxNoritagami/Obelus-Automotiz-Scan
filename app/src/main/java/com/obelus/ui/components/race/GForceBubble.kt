package com.obelus.ui.components.race

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.obelus.ui.theme.GForceHigh
import com.obelus.ui.theme.GForceLow
import com.obelus.ui.theme.GForceMax
import kotlin.math.abs

@Composable
fun GForceBubble(
    gForceX: Float = 0f, // Lateral
    gForceY: Float = 0f, // Longitudinal (Acce/Brake)
    modifier: Modifier = Modifier
) {
    // Clamping values para mantener la burbuja dentro del circulo (-1.5g a 1.5g aprox)
    val maxG = 1.5f
    val clampedX = gForceX.coerceIn(-maxG, maxG)
    val clampedY = gForceY.coerceIn(-maxG, maxG)
    
    val totalG = kotlin.math.sqrt(gForceX * gForceX + gForceY * gForceY)

    val bubbleColor by animateColorAsState(
        targetValue = when {
            totalG > 1.0f -> GForceMax
            totalG > 0.5f -> GForceHigh
            else -> GForceLow
        },
        animationSpec = tween(300), label = "gColor"
    )

    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2f
            
            // Fondo
            drawCircle(
                color = Color(0xFF1E1E28).copy(alpha = 0.8f),
                radius = radius,
                center = center
            )
            // Anillos Guia (0.5g, 1.0g)
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = radius * 0.33f,
                center = center,
                style = Stroke(2f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = radius * 0.66f,
                center = center,
                style = Stroke(2f)
            )
            // Borde
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = radius,
                center = center,
                style = Stroke(4f)
            )

            // Burbuja
            val targetOffsetX = center.x + (clampedX / maxG) * radius
            val targetOffsetY = center.y - (clampedY / maxG) * radius // -y es hacia arriba visualmente
            
            drawCircle(
                color = bubbleColor,
                radius = 16f,
                center = Offset(targetOffsetX, targetOffsetY)
            )
            
            // Efecto brillo de la burbuja
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = 6f,
                center = Offset(targetOffsetX - 4f, targetOffsetY - 4f)
            )
        }
    }
}
