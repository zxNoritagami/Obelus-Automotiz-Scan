package com.obelus.ui.components.logs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.ui.theme.DeepSurface
import com.obelus.ui.theme.NeonCyan

@Composable
fun SignalCard(
    signalName: String,
    currentValue: Float,
    unit: String,
    minValue: Float,
    maxValue: Float,
    recentData: List<Float>, // Mini points for sparkline
    modifier: Modifier = Modifier
) {
    // Alerta de color dinámica si roza máximo/mínimo absoluto (Simulación estética)
    val colorHighlight by animateColorAsState(
        targetValue = if (currentValue >= maxValue * 0.9f) Color(0xFFFF3366) else NeonCyan,
        animationSpec = tween(500), label = "valueColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSurface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            // Fondo animado: Sparkline Draw
            if (recentData.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize().padding(top = 40.dp, bottom = 10.dp)) {
                    val w = size.width
                    val h = size.height
                    val maxD = recentData.maxOrNull() ?: 1f
                    val minD = recentData.minOrNull() ?: 0f
                    val range = maxOf(0.1f, maxD - minD)
                    val stepX = w / maxOf(1, recentData.size - 1)

                    val path = Path()
                    recentData.forEachIndexed { idx, v ->
                        val x = idx * stepX
                        val normalizedY = (v - minD) / range
                        val y = h * (1f - normalizedY)

                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = Color.Gray.copy(alpha = 0.3f),
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                }
            }

            // Textos Foreground
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = signalName.uppercase(),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Min: ${String.format("%.1f", minValue)}", color = Color.Gray.copy(0.7f), fontSize = 10.sp)
                        Text("Max: ${String.format("%.1f", maxValue)}", color = Color.Gray.copy(0.7f), fontSize = 10.sp)
                    }
                }
                
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format("%.2f", currentValue),
                        color = colorHighlight,
                        fontSize = 32.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = unit, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        }
    }
}
