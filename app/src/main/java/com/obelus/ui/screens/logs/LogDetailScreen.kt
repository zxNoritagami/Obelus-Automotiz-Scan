package com.obelus.ui.screens.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.data.local.entity.DecodedSignal
import com.obelus.ui.components.logs.SignalChart
import com.obelus.ui.theme.DarkBackground
import com.obelus.ui.theme.DeepSurface
import com.obelus.ui.theme.NeonCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailScreen(
    signals: List<Pair<List<Pair<Long, Float>>, Color>>,
    stats: List<SignalStat> = emptyList(), // Simulated Data for Stats
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ANÁLISIS DE SEÑALES", color = Color.White, letterSpacing = 1.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Chart Area (Zoomable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .background(DeepSurface)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offsetX += pan.x
                        }
                    }
            ) {
                // To keep it simple in UI context without writing a complex transformation matrix,
                // we would normally pass scale and offset to SignalChart. We pass the data here directly.
                SignalChart(signals = signals, modifier = Modifier.fillMaxSize())

                Text(
                    "Pinch to Zoom & Drag to Pan",
                    color = Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // Stats Bottom Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    "ESTADÍSTICAS DE SESIÓN",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                stats.forEach { stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(stat.color, shape = androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(stat.name, color = Color.White, fontSize = 14.sp)
                        }
                        Text(
                            "${String.format("%.2f", stat.average)} avg  |  ${String.format("%.2f", stat.max)} peak",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Export Button
                Button(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("EXPORTAR CSV / PNG", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

data class SignalStat(val name: String, val color: Color, val average: Float, val max: Float)
