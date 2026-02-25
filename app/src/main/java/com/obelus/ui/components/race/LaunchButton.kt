package com.obelus.ui.components.race

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.obelusscan.domain.model.RaceState
import com.obelus.ui.theme.NeonCyan
import com.obelus.ui.theme.NeonGreen
import com.obelus.ui.theme.RaceAccent
import kotlinx.coroutines.delay

@Composable
fun LaunchButton(
    state: RaceState,
    onLaunchClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var countdownValue by remember { mutableStateOf(3) }
    
    // Efecto de Countdown si el estado muta (Simulado para que el UI interactue si backend lo dicta)
    // Usualmente el backend dice COUNTDOWN, aqui asumiremos q controlamos lo visual
    LaunchedEffect(state) {
        if (state == RaceState.COUNTDOWN) {
            countdownValue = 3
            while(countdownValue > 0) {
                delay(1000)
                countdownValue--
            }
            // Automáticamente transiciona a GO si el viewmodel no ha reaccionado (Fallback visual)
        }
    }

    Box(
        modifier = modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(
                when (state) {
                    RaceState.ARMED -> NeonCyan.copy(alpha = 0.2f)
                    RaceState.COUNTDOWN -> RaceAccent.copy(alpha = 0.8f)
                    RaceState.RUNNING -> NeonGreen.copy(alpha = 0.2f)
                    else -> Color.Gray.copy(alpha = 0.2f)
                }
            )
            .clickable(
                enabled = state == RaceState.ARMED || state == RaceState.RUNNING || state == RaceState.COUNTDOWN,
                onClick = {
                    if (state == RaceState.ARMED) onLaunchClick()
                    else if (state == RaceState.RUNNING || state == RaceState.COUNTDOWN) onCancelClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (state == RaceState.ARMED || state == RaceState.RUNNING) {
            com.obelus.ui.components.animations.PulseRing(
                modifier = Modifier.fillMaxSize(),
                color = if (state == RaceState.ARMED) NeonCyan else RaceAccent
            )
        }
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
            }, label = "LaunchAnimation"
        ) { targetState ->
            when (targetState) {
                RaceState.ARMED -> {
                    Text("LAUNCH", color = NeonCyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                RaceState.COUNTDOWN -> {
                    val displayTxt = if (countdownValue > 0) countdownValue.toString() else "GO"
                    Text(
                        text = displayTxt,
                        color = Color.White,
                        fontSize = if (displayTxt == "GO") 48.sp else 64.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                RaceState.RUNNING -> {
                    Text("STOP", color = RaceAccent, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                else -> {
                    Text("WAIT", color = Color.Gray, fontSize = 20.sp)
                }
            }
        }
    }
}
