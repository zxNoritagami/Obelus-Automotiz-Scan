package com.obelus.obelusscan.ui.race

import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.obelus.domain.race.RaceAnalysisResult
import com.obelus.obelusscan.domain.model.RaceSession
import com.obelus.obelusscan.domain.model.RaceState
import com.obelus.obelusscan.domain.model.RaceType
import com.obelus.obelusscan.domain.model.SplitTime
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RaceScreen(
    viewModel: RaceModeViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val state          by viewModel.raceState.collectAsState()
    val speed          by viewModel.currentSpeed.collectAsState()
    val session        by viewModel.session.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val savedAsRef     by viewModel.savedAsReference.collectAsState()
    val context        = LocalContext.current

    val backgroundColor by animateColorAsState(
        targetValue = when (state) {
            RaceState.IDLE     -> Color(0xFF121212)
            RaceState.ARMED    -> Color(0xFF2E2E2E)
            RaceState.RUNNING  -> Color(0xFF0A2F0A)
            RaceState.FINISHED -> Color(0xFF0D1B3E)
            RaceState.ERROR    -> Color(0xFF3B0000)
            else               -> Color(0xFF121212)
        },
        animationSpec = tween(durationMillis = 500), label = "bgColor"
    )

    // Toast when marked as reference
    LaunchedEffect(savedAsRef) {
        if (savedAsRef) {
            Toast.makeText(context, "⭐ Sesión guardada como referencia", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Crossfade(targetState = state, label = "RaceStateCrossfade") { currentState ->
            when (currentState) {
                RaceState.IDLE -> RaceIdleContent(
                    onArmRace = { type -> viewModel.armRace(type) },
                    onHistory = onNavigateToHistory
                )
                RaceState.ARMED, RaceState.COUNTDOWN, RaceState.RUNNING -> RaceRunningContent(
                    state    = currentState,
                    speed    = speed,
                    progress = viewModel.getProgress(),
                    session  = session,
                    onCancel = { viewModel.cancelRace() }
                )
                RaceState.FINISHED -> RaceResultsContent(
                    session         = session,
                    analysisResult  = analysisResult,
                    savedAsRef      = savedAsRef,
                    onSaveReference = { viewModel.saveAsReference() },
                    onReset         = { viewModel.cancelRace() }
                )
                else -> {}
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Idle content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RaceIdleContent(onArmRace: (RaceType) -> Unit, onHistory: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
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
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = "Race Flag",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Selecciona Modalidad",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(32.dp))
        RaceModeButton("0 → 100 km/h", Icons.Default.Timer)       { onArmRace(RaceType.ACCELERATION_0_100) }
        Spacer(Modifier.height(16.dp))
        RaceModeButton("0 → 200 km/h", Icons.Default.PlayArrow)   { onArmRace(RaceType.ACCELERATION_0_200) }
        Spacer(Modifier.height(16.dp))
        RaceModeButton("100 → 0 km/h  (Frenada)", Icons.Default.Close) { onArmRace(RaceType.BRAKING_100_0) }
    }
}

@Composable
fun RaceModeButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(text = text, fontSize = 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Running content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RaceRunningContent(
    state: RaceState,
    speed: Int,
    progress: Float,
    session: RaceSession?,
    onCancel: () -> Unit
) {
    var elapsedTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        val t0 = System.currentTimeMillis()
        while (state == RaceState.RUNNING) {
            elapsedTime = System.currentTimeMillis() - t0
            kotlinx.coroutines.delay(30)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = if (state == RaceState.ARMED) "LISTO PARA SALIR" else "CARRERA EN CURSO",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (state == RaceState.ARMED) Color.Yellow else Color(0xFF66FF66)
            )
        }

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
            Spacer(Modifier.height(24.dp))
            Text(
                text = String.format(Locale.US, "%.2f s", elapsedTime / 1000f),
                style = MaterialTheme.typography.displayMedium,
                color = if (state == RaceState.ARMED) Color.Gray else Color(0xFF66FF66),
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
            modifier = Modifier.padding(bottom = 32.dp).fillMaxWidth()
        ) {
            Text("CANCELAR", color = Color.White)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Race Results (replaces old RaceFinishedContent)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RaceResultsContent(
    session: RaceSession?,
    analysisResult: RaceAnalysisResult?,
    savedAsRef: Boolean,
    onSaveReference: () -> Unit,
    onReset: () -> Unit
) {
    if (session == null) return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "RESULTADOS",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = session.type.displayName(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            // Final time
            Text(
                text = String.format(Locale.US, "%.3f s", session.finalTime),
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
        }

        // ── Stats row ────────────────────────────────────────────────────
        item {
            val maxG  = analysisResult?.maxGForce ?: session.maxGforce
            val hp    = analysisResult?.estimatedHp ?: 0f
            val react = analysisResult?.reactionTimeMs ?: 0L

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Max G",    String.format(Locale.US, "%.2f G",  maxG))
                StatItem("Est. HP",  if (hp > 0f) String.format(Locale.US, "%.0f HP", hp) else "—")
                StatItem("Reacción", if (react > 0) "${react} ms" else "—")
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))
        }

        // ── Speed vs Time chart ───────────────────────────────────────────
        item {
            if (analysisResult != null && analysisResult.speedTimeSeries.isNotEmpty()) {
                Text(
                    text = "Velocidad vs Tiempo",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
                SpeedTimeChart(
                    series   = analysisResult.speedTimeSeries,
                    maxSpeed = session.targetSpeedEnd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))
            }
        }

        // ── Splits table ─────────────────────────────────────────────────
        val splits = if (analysisResult?.splits?.isNotEmpty() == true) analysisResult.splits else session.times
        if (splits.isNotEmpty()) {
            item {
                Text(
                    text = "Splits cada 10 km/h",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Rango",       fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
                    Text("Parcial",     fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
                    Text("Acumulado",   fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
            }

            var cumulative = 0L
            items(splits) { split ->
                cumulative += split.timeMs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${split.speedFrom} → ${split.speedTo} km/h",
                        color = Color.White, fontSize = 13.sp
                    )
                    Text(
                        text = String.format(Locale.US, "+%.2f s", split.timeMs / 1000f),
                        color = MaterialTheme.colorScheme.primary, fontSize = 13.sp
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f s", cumulative / 1000f),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            }
        }

        // ── Action buttons ────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            if (!savedAsRef) {
                Button(
                    onClick = onSaveReference,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar como referencia")
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700))
                    Spacer(Modifier.width(8.dp))
                    Text("Sesión guardada como referencia", color = Color(0xFFFFD700))
                }
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
            ) {
                Text("NUEVA CARRERA")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas speed/time chart
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpeedTimeChart(
    series: List<Pair<Long, Int>>,
    maxSpeed: Int,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) return

    val lineColor   = Color(0xFF7C4DFF)
    val gridColor   = Color.White.copy(alpha = 0.1f)
    val labelColor  = Color.White.copy(alpha = 0.5f)

    Canvas(modifier = modifier) {
        val w     = size.width
        val h     = size.height
        val padL  = 40f
        val padB  = 32f
        val chartW = w - padL
        val chartH = h - padB

        val maxT = series.last().first.coerceAtLeast(1L)
        val maxV = maxSpeed.coerceAtLeast(series.maxOf { it.second }).toFloat()

        // Grid lines (5)
        for (i in 0..4) {
            val y = padB + chartH * (1f - i / 4f)
            drawLine(gridColor, Offset(padL, y), Offset(w, y), strokeWidth = 1f)
        }

        // Speed line
        val path = Path()
        series.forEachIndexed { idx, (t, v) ->
            val x = padL + (t.toFloat() / maxT) * chartW
            val y = padB + chartH * (1f - v.toFloat() / maxV)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

        // Dots at start and end
        series.firstOrNull()?.let { (t, v) ->
            val x = padL + (t.toFloat() / maxT) * chartW
            val y = padB + chartH * (1f - v.toFloat() / maxV)
            drawCircle(lineColor, 5f, Offset(x, y))
        }
        series.lastOrNull()?.let { (t, v) ->
            val x = padL + (t.toFloat() / maxT) * chartW
            val y = padB + chartH * (1f - v.toFloat() / maxV)
            drawCircle(Color.White, 5f, Offset(x, y))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

fun RaceType.displayName(): String = when (this) {
    RaceType.ACCELERATION_0_100 -> "Aceleración 0 → 100 km/h"
    RaceType.ACCELERATION_0_200 -> "Aceleración 0 → 200 km/h"
    RaceType.BRAKING_100_0      -> "Frenada 100 → 0 km/h"
    RaceType.CUSTOM             -> "Carrera personalizada"
}
