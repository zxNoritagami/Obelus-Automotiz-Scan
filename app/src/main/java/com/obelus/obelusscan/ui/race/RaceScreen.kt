package com.obelus.obelusscan.ui.race

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.obelusscan.domain.model.RaceSession
import com.obelus.obelusscan.domain.model.RaceState
import com.obelus.obelusscan.domain.model.RaceType
import java.util.Locale

@Composable
fun RaceScreen(
    viewModel: RaceModeViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.raceState.collectAsState()
    val speed by viewModel.currentSpeed.collectAsState()
    val session by viewModel.session.collectAsState()

    // Animación de fondo suave según estado
    val backgroundColor by animateColorAsState(
        targetValue = when (state) {
            RaceState.IDLE -> MaterialTheme.colorScheme.background
            RaceState.ARMED -> Color(0xFF2E2E2E) // Grid oscuro
            RaceState.RUNNING -> Color(0xFF1B5E20) // Verde muy oscuro
            RaceState.FINISHED -> Color(0xFF0D47A1) // Azul muy oscuro
            RaceState.ERROR -> Color(0xFFB71C1C) // Rojo oscuro
            else -> MaterialTheme.colorScheme.background
        },
        animationSpec = tween(durationMillis = 500), label = "bgColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Crossfade(targetState = state, label = "RaceStateCrossfade") { currentState ->
            when (currentState) {
                RaceState.IDLE -> RaceIdleContent(
                    onArmRace = { type -> viewModel.armRace(type) }
                )
                RaceState.ARMED, RaceState.COUNTDOWN, RaceState.RUNNING -> RaceRunningContent(
                    state = currentState,
                    speed = speed,
                    session = session,
                    onCancel = { viewModel.cancelRace() }
                )
                RaceState.FINISHED -> RaceFinishedContent(
                    session = session,
                    onReset = { viewModel.cancelRace() } // Cancel vuelve a IDLE
                )
                else -> { /* Error handled visually by color */ }
            }
        }
    }
}

@Composable
fun RaceIdleContent(onArmRace: (RaceType) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = "Race Flag",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Selecciona Modalidad",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))

        RaceModeButton("0 - 100 km/h", Icons.Default.Timer) {
            onArmRace(RaceType.ACCELERATION_0_100)
        }
        Spacer(modifier = Modifier.height(16.dp))
        RaceModeButton("0 - 200 km/h", Icons.Default.PlayArrow) {
            onArmRace(RaceType.ACCELERATION_0_200)
        }
        Spacer(modifier = Modifier.height(16.dp))
        RaceModeButton("100 - 0 km/h (Frenada)", Icons.Default.Close) {
            onArmRace(RaceType.BRAKING_100_0)
        }
    }
}

@Composable
fun RaceModeButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, fontSize = 18.sp)
    }
}

@Composable
fun RaceRunningContent(
    state: RaceState,
    speed: Int,
    session: RaceSession?,
    onCancel: () -> Unit
) {
    // Cronómetro visual simulado
    var elapsedTime by remember { mutableLongStateOf(0L) }
    
    // Si está corriendo, actualizamos la ui cada frame (o casi)
    LaunchedEffect(state) {
        val startTime = System.currentTimeMillis()
        while (state == RaceState.RUNNING) {
            elapsedTime = System.currentTimeMillis() - startTime
            kotlinx.coroutines.delay(30) // ~30fps update
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: Status
        Text(
            text = if (state == RaceState.ARMED) "LISTO PARA SALIR" else "CARRERA EN CURSO",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Black
        )

        // Center: Speed & Timer
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$speed",
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Timer Display
            val seconds = elapsedTime / 1000f
            Text(
                text = String.format(Locale.US, "%.2f s", seconds),
                style = MaterialTheme.typography.displayMedium,
                color = if (state == RaceState.ARMED) Color.Gray else MaterialTheme.colorScheme.primaryContainer,
                fontWeight = FontWeight.Bold
            )
        }

        // Bottom: Cancel Button
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text("CANCELAR", color = Color.White)
        }
    }
}

@Composable
fun RaceFinishedContent(
    session: RaceSession?,
    onReset: () -> Unit
) {
    if (session == null) return

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "RESULTADOS",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Tiempo Final Grande
        Text(
            text = String.format(Locale.US, "%.2f s", session.finalTime),
            fontSize = 80.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        // Stats Rápidos
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Max G", String.format("%.2f G", session.maxGforce))
            StatItem("Vel. Final", "${session.targetSpeedEnd} km/h")
        }

        Divider(color = Color.White.copy(alpha = 0.2f))

        // Tabla de Splits (Scrollable)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Rango", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("Tiempo", fontWeight = FontWeight.Bold, color = Color.Gray)
                }
                Spacer(Modifier.height(8.dp))
            }
            items(session.times) { split ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${split.speedFrom} - ${split.speedTo} km/h",
                        color = Color.White
                    )
                    Text(
                        text = String.format(Locale.US, "+ %.2f s", split.timeMs / 1000f),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Divider(color = Color.White.copy(alpha = 0.1f))
            }
        }

        Button(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp)
        ) {
            Text("NUEVA CARRERA")
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
