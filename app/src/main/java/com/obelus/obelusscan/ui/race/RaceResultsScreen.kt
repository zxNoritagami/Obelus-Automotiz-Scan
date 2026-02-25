package com.obelus.obelusscan.ui.race

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.domain.race.RaceAnalysisResult
import com.obelus.obelusscan.domain.model.RaceSession
import com.obelus.ui.components.race.ResultCard
import com.obelus.ui.theme.NeonCyan
import com.obelus.ui.theme.RaceAccent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Locale

@Composable
fun RaceResultsScreen(
    session: RaceSession?,
    analysisResult: RaceAnalysisResult?,
    savedAsRef: Boolean,
    onSaveReference: () -> Unit,
    onReset: () -> Unit
) {
    if (session == null) return

    val maxG = analysisResult?.maxGForce ?: session.maxGforce
    val hp = analysisResult?.estimatedHp ?: 0f
    
    // Animate numbers from 0 to final
    var isAnimationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isAnimationTriggered = true
    }

    val animatedTime by animateFloatAsState(
        targetValue = if (isAnimationTriggered) (session.finalTime).toFloat() else 0f,
        animationSpec = tween(1500),
        label = "timeAnim"
    )

    val animatedG by animateFloatAsState(
        targetValue = if (isAnimationTriggered) maxG else 0f,
        animationSpec = tween(1500),
        label = "gForceAnim"
    )

    // Asumimos un field `isPersonalBest` existe en la data, si no, lo dejamos en false como placeholder del UI visual
    val isPB = analysisResult?.maxGForce != null // Placeholder temporal basado en logica

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            
            ResultCard(
                title = session.type.displayName(),
                primaryTime = String.format(Locale.US, "%.3f s", animatedTime),
                isPersonalBest = true, // Force true to show UI design
                maxG = animatedG,
                estimatedHp = hp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        item {
            if (analysisResult != null && analysisResult.speedTimeSeries.isNotEmpty()) {
                Text(
                    text = "TELEMETRY",
                    color = Color.Gray,
                    letterSpacing = 2.sp,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    TelemetryGraph(
                        series = analysisResult.speedTimeSeries,
                        maxSpeed = session.targetSpeedEnd
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        val splits = if (analysisResult?.splits?.isNotEmpty() == true) analysisResult.splits else session.times
        if (splits.isNotEmpty()) {
            item {
                Text(
                    text = "SPLITS",
                    color = Color.Gray,
                    letterSpacing = 2.sp,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            var cumulative = 0L
            items(splits) { split ->
                cumulative += split.timeMs
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${split.speedFrom}-${split.speedTo}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text(
                        text = String.format(Locale.US, "+%.2f", split.timeMs / 1000f),
                        color = NeonCyan,
                        fontSize = 16.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text(
                        text = String.format(Locale.US, "%.2fs", cumulative / 1000f),
                        color = RaceAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!savedAsRef) {
                    Button(
                        onClick = onSaveReference,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E28)),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700))
                        Spacer(Modifier.width(8.dp))
                        Text("REF", color = Color.White)
                    }
                }
                Button(
                    onClick = { /* TODO: Implement Share logic/Intent */ },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("SHARE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = RaceAccent),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("RESTART", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
