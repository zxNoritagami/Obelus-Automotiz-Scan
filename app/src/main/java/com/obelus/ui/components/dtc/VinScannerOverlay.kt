package com.obelus.ui.components.dtc

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.obelus.ui.theme.NeonCyan

@Composable
fun VinScannerOverlay(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isScanning) return

    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    
    // Anima de 0f a 1f para representar el Progreso Y (0% to 100% de la altura)
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        // La barra contenedora de escaneo viaja por la pantalla hacia abajo
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    // Uso de fillMaxSize superior, por lo que usamos un truco con fillMaxHeight
                    // offset con fracción para que baje en base al padre.
                    // Pero Compose modifier offset no agarra fracciones float directamente sin onGloballyPositioned,
                    // Simplificaremos empujando la linea laser con Weight 
            )
        }

        // Custom Scanner Laser Line (Absolute Positioned via constraints logic in Canvas or Box)
        // Para simplificar sin LayoutBuilder intermedio, usamos un Box overlay gigante con un gradient que se mueve
        val gradientAlpha = 1f - (scanProgress * 2f - 1f).let { kotlin.math.abs(it) } // Fade out at edges si queremos
        
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val yOffset = size.height * scanProgress
                // Linea Laser fuerte
                drawLine(
                    color = NeonCyan,
                    start = androidx.compose.ui.geometry.Offset(0f, yOffset),
                    end = androidx.compose.ui.geometry.Offset(size.width, yOffset),
                    strokeWidth = 6f
                )
                
                // Efecto de barrido (Alpha brush)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0f), NeonCyan.copy(alpha = 0.3f), NeonCyan),
                        startY = maxOf(0f, yOffset - 150f),
                        endY = yOffset
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, maxOf(0f, yOffset - 150f)),
                    size = androidx.compose.ui.geometry.Size(size.width, minOf(yOffset, 150f))
                )
            }
        }
    }
}
