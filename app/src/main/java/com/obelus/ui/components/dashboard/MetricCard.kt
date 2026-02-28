package com.obelus.ui.components.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.obelus.ui.theme.DeepSurface
import kotlinx.coroutines.delay

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    delayMillis: Long = 0,
    accentColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit
) {
    val isVisible = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delayMillis)
        isVisible.value = true
    }

    AnimatedVisibility(
        visible = isVisible.value,
        enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
            animationSpec = tween(600),
            initialOffsetY = { 40 }
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E1E26),
                            Color(0xFF12121A)
                        )
                    )
                )
                // Dibujar rejilla técnica sutil de fondo
                .drawBehind {
                    val gridSize = 20.dp.toPx()
                    for (x in 0..size.width.toInt() step gridSize.toInt()) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.03f),
                            start = Offset(x.toFloat(), 0f),
                            end = Offset(x.toFloat(), size.height),
                            strokeWidth = 1f
                        )
                    }
                    for (y in 0..size.height.toInt() step gridSize.toInt()) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.03f),
                            start = Offset(0f, y.toFloat()),
                            end = Offset(size.width, y.toFloat()),
                            strokeWidth = 1f
                        )
                    }
                }
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            accentColor.copy(alpha = 0.4f),
                            accentColor.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            content = content
        )
    }
}
