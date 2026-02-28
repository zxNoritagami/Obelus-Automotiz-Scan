package com.obelus.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.ui.theme.NeonCyan
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.local.entity.RaceRecord
import com.obelus.presentation.viewmodel.HistoryViewModel

private val BgDark        = Color(0xFF0D1117)
private val BgPanel       = Color(0xFF161B22)
private val TextPrimary   = Color(0xFFE6EDF3)
private val TextMuted     = Color(0xFF8B949E)
private val Gold          = Color(0xFFFFD700)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    raceId: Long,
    viewModel: HistoryViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    var race by remember { mutableStateOf<RaceRecord?>(null) }
    
    LaunchedEffect(raceId) {
        race = viewModel.getRace(raceId)
    }

    // Scaffold para la vista detallada
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalles de Carrera", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Exportar a CSV/PDF */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar", tint = NeonCyan)
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
            if (race == null) {
                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.padding(top = 64.dp))
            } else {
                val currentRace = race!!
                // Header
                Text(
                    text = currentRace.raceType, 
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = String.format("%.2f s", currentRace.finalTimeSeconds),
                    fontSize = 48.sp,
                    color = Gold,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                if(currentRace.isPersonalBest) {
                    Surface(
                        color = Gold.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        Text(
                            "★ NUEVO RECORD PERSONAL ★",
                            color = Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }

            // Gráfico Simulado de Velocidad
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(containerColor = BgPanel),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, null, tint = TextMuted)
                        Spacer(Modifier.height(8.dp))
                        Text("Gráfico de Telemetría (Velocidad vs Tiempo)", color = TextMuted, fontSize = 12.sp)
                        Text("Se renderizará Canvas 2D aquí", color = NeonCyan, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Estadísticas adicionales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(title = "Top Speed", value = "${currentRace.targetSpeedEnd}", unit = "km/h", modifier = Modifier.weight(1f))
                StatCard(title = "Max G-Force", value = String.format("%.1f", currentRace.maxGForce), unit = "G", modifier = Modifier.weight(1f))
            }
            } // Close currentRace block
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = BgPanel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(4.dp))
                Text(unit, color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}
