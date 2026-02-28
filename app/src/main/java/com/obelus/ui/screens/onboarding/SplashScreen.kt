package com.obelus.ui.screens.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    var isAnimTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isAnimTriggered = true
        delay(2000L) 
        onTimeout()
    }

    val scale by animateFloatAsState(
        targetValue = if (isAnimTriggered) 1f else 0.8f,
        animationSpec = tween(1500),
        label = "LogoScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isAnimTriggered) 1f else 0f,
        animationSpec = tween(1500),
        label = "LogoAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030303)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.logo_obelus),
                contentDescription = "Obelus Logo",
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .alpha(alpha)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "OBELUS SCANNER",
                color = Color(0xFF00E5FF).copy(alpha = alpha),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                modifier = Modifier.alpha(alpha)
            )
            
            Text(
                text = "SYSTEM INITIALIZING...",
                color = Color.Gray.copy(alpha = alpha),
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(alpha).padding(top = 8.dp)
            )
        }
    }
}
