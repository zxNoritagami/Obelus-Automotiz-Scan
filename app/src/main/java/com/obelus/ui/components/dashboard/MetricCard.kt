package com.obelus.ui.components.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    // Animación de entrada escalonada
    LaunchedEffect(Unit) {
        delay(delayMillis)
        isVisible.value = true
    }

    AnimatedVisibility(
        visible = isVisible.value,
        enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
            animationSpec = tween(500),
            initialOffsetY = { 50 }
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            DeepSurface.copy(alpha = 0.8f),
                            DeepSurface.copy(alpha = 0.6f)
                        )
                    )
                )
                // Efecto cristal: borde superior/izquierdo claro, inferior/derecho acento si lo hay
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            accentColor.copy(alpha = 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp),
            content = content
        )
    }
}
