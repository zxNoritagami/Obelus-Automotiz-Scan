package com.obelus.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircularGauge(
    value: Float,
    maxValue: Float,
    unit: String,
    label: String,
    color: Color = Color(0xFF00E5FF),
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, maxValue),
        animationSpec = tween(durationMillis = 400),
        label = "GaugeAnimation"
    )

    val progress = animatedValue / maxValue
    val startAngle = 150f
    val sweepAngle = 240f

    Box(
        modifier = modifier.padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(12.dp)
                .alpha(0.3f)
        ) {
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize().aspectRatio(1f)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - strokeWidth.toPx()

            drawArc(
                color = Color(0xFF1A1A1A),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )

            val tickCount = 10
            for (i in 0..tickCount) {
                val angle = startAngle + (sweepAngle / tickCount) * i
                val angleRad = Math.toRadians(angle.toDouble())
                val innerRadius = radius - 15.dp.toPx()
                val outerRadius = radius - 5.dp.toPx()
                
                val start = Offset(
                    (center.x + innerRadius * cos(angleRad)).toFloat(),
                    (center.y + innerRadius * sin(angleRad)).toFloat()
                )
                val end = Offset(
                    (center.x + outerRadius * cos(angleRad)).toFloat(),
                    (center.y + outerRadius * sin(angleRad)).toFloat()
                )
                
                drawLine(
                    color = if (angle <= startAngle + sweepAngle * progress) color else Color.Gray.copy(alpha = 0.3f),
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to color.copy(alpha = 0.5f),
                    progress to color,
                    1.0f to color,
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
            
            val pointerAngle = startAngle + sweepAngle * progress
            val pointerRad = Math.toRadians(pointerAngle.toDouble())
            val pointerPos = Offset(
                (center.x + radius * cos(pointerRad)).toFloat(),
                (center.y + radius * sin(pointerRad)).toFloat()
            )
            
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = pointerPos
            )
            drawCircle(
                color = color,
                radius = 8.dp.toPx(),
                center = pointerPos,
                alpha = 0.5f
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(
                text = String.format("%.0f", animatedValue),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-1).sp
            )
            Text(
                text = unit.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = 0.8f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                color = Color.Gray,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
fun LinearGauge(
    value: Float,
    maxValue: Float,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF3D00)
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, maxValue),
        animationSpec = tween(durationMillis = 400),
        label = "LinearGaugeAnimation"
    )
    val progress = animatedValue / maxValue

    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
            Text(
                text = "${String.format("%.1f", animatedValue)} $unit",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
            val segments = 20
            val spacing = 4.dp.toPx()
            val segmentWidth = (size.width - (spacing * (segments - 1))) / segments
            
            for (i in 0 until segments) {
                val isActive = i < segments * progress
                val segmentColor = if (isActive) {
                    when {
                        i > segments * 0.85 -> Color.Red
                        i > segments * 0.6 -> Color.Yellow
                        else -> color
                    }
                } else {
                    Color(0xFF222222)
                }
                
                drawRoundRect(
                    color = segmentColor,
                    topLeft = Offset(i * (segmentWidth + spacing), 0f),
                    size = Size(segmentWidth, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                
                if (isActive) {
                    drawRect(
                        color = segmentColor.copy(alpha = 0.2f),
                        topLeft = Offset(i * (segmentWidth + spacing) - 2.dp.toPx(), -2.dp.toPx()),
                        size = Size(segmentWidth + 4.dp.toPx(), size.height + 4.dp.toPx())
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun NewGaugePreview() {
    Box(modifier = Modifier.background(Color.Black).padding(24.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                CircularGauge(
                    value = 4500f,
                    maxValue = 8000f,
                    unit = "rpm",
                    label = "Engine Speed",
                    modifier = Modifier.weight(1f).aspectRatio(1f)
                )
                CircularGauge(
                    value = 120f,
                    maxValue = 240f,
                    unit = "km/h",
                    label = "Vehicle Speed",
                    color = Color(0xFF7C4DFF),
                    modifier = Modifier.weight(1f).aspectRatio(1f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            LinearGauge(
                value = 82f,
                maxValue = 100f,
                unit = "%",
                label = "Throttle Position"
            )
        }
    }
}
