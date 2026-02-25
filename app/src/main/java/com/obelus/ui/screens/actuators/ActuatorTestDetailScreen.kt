package com.obelus.ui.screens.actuators

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.obelusscan.ui.theme.NeonCyan
import com.obelus.ui.components.actuators.TestProgressIndicator
import kotlinx.coroutines.delay

private val BgDark      = Color(0xFF0D1117)
private val BgPanel     = Color(0xFF161B22)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextMuted   = Color(0xFF8B949E)
private val SuccessCol  = Color(0xFF3FB950)
private val ErrorCol    = Color(0xFFF85149)

enum class TestState { IDLE, RUNNING, COMPLETED, ABORTED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActuatorTestDetailScreen(
    categoryId: Int,
    onBack: () -> Unit
) {
    var state by remember { mutableStateOf(TestState.IDLE) }
    var timeLeft by remember { mutableStateOf(10000L) } // 10s countdown
    val totalTime = 10000f

    LaunchedEffect(state) {
        if (state == TestState.RUNNING) {
            timeLeft = 10000L
            while (timeLeft > 0 && state == TestState.RUNNING) {
                delay(10) // 100 FPS
                timeLeft -= 10
            }
            if (state == TestState.RUNNING) {
                state = TestState.COMPLETED
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ejecución (Motor/Fans)", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = state != TestState.RUNNING) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Info
            Card(
                colors = CardDefaults.cardColors(containerColor = BgPanel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Activar Electroventilador 1", color = NeonCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Este comando encenderá el ventilador principal a máxima velocidad por 10 segundos para verificar su integridad física y relees.",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("CONDICIONES OBLIGATORIAS:", color = Warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("• Motor debe estar en RPM 0", color = TextPrimary, fontSize = 12.sp)
                    Text("• Llave en posición ON (contacto)", color = TextPrimary, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            // Progress Area
            AnimatedVisibility(
                visible = state == TestState.RUNNING,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                TestProgressIndicator(
                    progress = 1f - (timeLeft / totalTime),
                    timeLeftMs = timeLeft,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            // Result Area
            AnimatedVisibility(
                visible = state == TestState.COMPLETED || state == TestState.ABORTED,
                enter = fadeIn()
            ) {
                val isSuccess = state == TestState.COMPLETED
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = "Result",
                        tint = if (isSuccess) SuccessCol else ErrorCol,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (isSuccess) "Test Completado Exitosamente" else "Test Abortado Manualmente",
                        color = if (isSuccess) SuccessCol else ErrorCol,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Action Buttons
            if (state == TestState.RUNNING) {
                Button(
                    onClick = { state = TestState.ABORTED },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorCol),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("PARADA DE EMERGENCIA", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = { state = TestState.RUNNING },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state == TestState.IDLE) "INICIAR TEST" else "REPETIR TEST", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }
    }
}

val Warn = Color(0xFFFFAA00)
