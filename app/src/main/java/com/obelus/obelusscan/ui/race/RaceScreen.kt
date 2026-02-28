package com.obelus.obelusscan.ui.race

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.obelusscan.domain.model.RaceState
import com.obelus.obelusscan.domain.model.RaceType
import com.obelus.ui.components.race.GForceBubble
import com.obelus.ui.components.race.LaunchButton
import com.obelus.ui.components.race.RaceGauge
import com.obelus.ui.theme.DarkBackground

@Composable
fun RaceScreen(
    viewModel: RaceModeViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val state by viewModel.raceState.collectAsState()
    val speed by viewModel.currentSpeed.collectAsState()
    val session by viewModel.session.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val savedAsRef by viewModel.savedAsReference.collectAsState()
    val context = LocalContext.current
    
    // Placeholder flow para forces si no exiten (evita crash)
    // val currentGForceX by viewModel.gForceX.collectAsState(initial = 0f) 
    // val currentGForceY by viewModel.gForceY.collectAsState(initial = 0f)

    LaunchedEffect(savedAsRef) {
        if (savedAsRef) {
            Toast.makeText(context, "⭐ Sesión guardada como referencia", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Crossfade(targetState = state, label = "RaceStateCrossfade") { currentState ->
            when (currentState) {
                RaceState.IDLE -> RaceIdleContent(
                    onArmRace = { type -> viewModel.armRace(type) },
                    onHistory = onNavigateToHistory
                )
                RaceState.ARMED, RaceState.COUNTDOWN, RaceState.RUNNING -> RaceRunningContent(
                    state = currentState,
                    speed = speed,
                    gForceX = 0.5f, // Placeholder dinamico visual
                    gForceY = 0.8f, // Placeholder dinamico visual
                    onLaunchClick = { /* ViewModel might auto launch or need trigger */ },
                    onCancelClick = { viewModel.cancelRace() }
                )
                RaceState.FINISHED -> RaceResultsScreen(
                    session = session,
                    analysisResult = analysisResult,
                    savedAsRef = savedAsRef,
                    onSaveReference = { viewModel.saveAsReference() },
                    onReset = { viewModel.cancelRace() }
                )
                else -> {}
            }
        }
    }
}

@Composable
fun RaceIdleContent(onArmRace: (RaceType) -> Unit, onHistory: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onHistory) {
                Icon(
                    Icons.Default.History,
                    contentDescription = "Historial de Carreras",
                    tint = Color.White
                )
            }
        }
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = "Race Flag",
            modifier = Modifier.size(80.dp),
            tint = com.obelus.ui.theme.NeonCyan
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "RACE MODE",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = Color.White
        )
        Spacer(Modifier.height(48.dp))
        RaceModeButton("0 → 100 km/h", Icons.Default.Timer)       { onArmRace(RaceType.ACCELERATION_0_100) }
        Spacer(Modifier.height(16.dp))
        RaceModeButton("0 → 200 km/h", Icons.Default.PlayArrow)   { onArmRace(RaceType.ACCELERATION_0_200) }
        Spacer(Modifier.height(16.dp))
        RaceModeButton("100 → 0 km/h", Icons.Default.Close) { onArmRace(RaceType.BRAKING_100_0) }
    }
}

@Composable
fun RaceModeButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E28)),
        shape = MaterialTheme.shapes.large
    ) {
        Icon(icon, contentDescription = null, tint = com.obelus.ui.theme.NeonCyan)
        Spacer(Modifier.width(16.dp))
        Text(text = text, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RaceRunningContent(
    state: RaceState,
    speed: Int,
    gForceX: Float,
    gForceY: Float,
    onLaunchClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Main Speedometer Full Immersive
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.6f),
                contentAlignment = Alignment.Center
            ) {
                RaceGauge(speed = speed, maxSpeed = 260, modifier = Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$speed",
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text(
                        text = "km/h",
                        fontSize = 20.sp,
                        color = Color.Gray,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Launch Button Centered Bottom
            LaunchButton(
                state = state,
                onLaunchClick = onLaunchClick,
                onCancelClick = onCancelClick,
                modifier = Modifier.padding(bottom = 64.dp)
            )
        }

        // Floating G-Force Bubble Bottom Right
        GForceBubble(
            gForceX = gForceX,
            gForceY = gForceY,
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.BottomEnd)
                .padding(bottom = 64.dp, end = 24.dp)
        )
    }
}

fun RaceType.displayName(): String = when (this) {
    RaceType.ACCELERATION_0_100 -> "0 → 100 km/h"
    RaceType.ACCELERATION_0_200 -> "0 → 200 km/h"
    RaceType.BRAKING_100_0      -> "Frecuancia 100 → 0"
    RaceType.CUSTOM             -> "Custom Run"
}
