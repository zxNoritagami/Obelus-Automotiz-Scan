package com.obelus.ui.components.logs

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.ui.theme.NeonCyan

@Composable
fun DbcImportOverlay(
    isImporting: Boolean,
    progress: Float, // 0f to 1f
    signalsFound: Int,
    modifier: Modifier = Modifier
) {
    if (!isImporting) return

    val infiniteTransition = rememberInfiniteTransition(label = "")
    val rotAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing)),
        label = "spin"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                // Background Track
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = Color.DarkGray,
                        startAngle = 0f, sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8f)
                    )
                    // Animated Arc Spinner
                    drawArc(
                        color = NeonCyan.copy(alpha = 0.5f),
                        startAngle = rotAngle, sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = 8f, cap = StrokeCap.Round)
                    )
                    // Actual Progress Arc
                    drawArc(
                        color = NeonCyan,
                        startAngle = -90f, sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 8f, cap = StrokeCap.Round)
                    )
                }
                
                // Texto Central
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("PARSING DBC MATRIX...", color = NeonCyan, letterSpacing = 2.sp, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("Señales decodificadas: $signalsFound", color = Color.Gray, fontFamily = FontFamily.Monospace)
        }
    }
}
