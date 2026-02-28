package com.obelus.obelusscan.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.domain.model.ObdPid
import com.obelus.ui.components.dashboard.GaugeArc
import com.obelus.ui.components.dashboard.MetricCard
import com.obelus.ui.components.dashboard.StatusBar
import com.obelus.ui.components.animations.shimmerEffect
import com.obelus.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val rpm by viewModel.rpm.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val coolantTemp by viewModel.coolantTemp.collectAsState()
    val throttlePos by viewModel.throttlePos.collectAsState()
    val engineLoad by viewModel.engineLoad.collectAsState()
    val mafRate by viewModel.mafRate.collectAsState()

    // Anomalías
    val rpmAnomaly by viewModel.rpmAnomaly.collectAsState()
    val tempAnomaly by viewModel.tempAnomaly.collectAsState()
    val throttleAnomaly by viewModel.throttleAnomaly.collectAsState()
    val loadAnomaly by viewModel.loadAnomaly.collectAsState()
    val mafAnomaly by viewModel.mafAnomaly.collectAsState()

    // Grabación
    val recordingState by viewModel.recordingState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startScanning()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(top = 8.dp, start = 12.dp, end = 12.dp)
        ) {
            StatusBar(isConnected = true, modifier = Modifier.padding(bottom = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val rpmColor = when {
                        rpm > 6000 -> NeonRed
                        rpm > 4000 -> NeonAmber
                        else -> NeonCyan
                    }

                    val animatedRpm by animateFloatAsState(
                        targetValue = rpm,
                        animationSpec = tween(durationMillis = 500),
                        label = "rpmAnim"
                    )

                    if (rpm == 0f) {
                        Box(modifier = Modifier.size(200.dp).clip(CircleShape).shimmerEffect())
                    } else {
                        AnomalyGaugeWrapper(isAnomalous = rpmAnomaly) {
                            GaugeArc(
                                value = animatedRpm,
                                minValue = ObdPid.RPM.minValue,
                                maxValue = ObdPid.RPM.maxValue,
                                activeColor = if (rpmAnomaly) NeonRed else rpmColor,
                                strokeWidth = 35f
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.0f", rpm),
                            style = FuturisticTypography.headlineLarge,
                            color = if (rpmAnomaly) NeonRed else rpmColor
                        )
                        Text(
                            text = "RPM",
                            style = FuturisticTypography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(0.4f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val animatedSpeed by animateFloatAsState(
                        targetValue = speed,
                        animationSpec = tween(durationMillis = 500),
                        label = "speedAnim"
                    )

                    if (speed == 0f && rpm == 0f) {
                       Box(modifier = Modifier.fillMaxWidth(0.6f).height(60.dp).shimmerEffect())
                    } else {
                        Text(
                            text = String.format("%.0f", animatedSpeed),
                            style = FuturisticTypography.headlineLarge,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "km/h",
                        style = FuturisticTypography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val tempColor = when {
                        coolantTemp < 70 -> NeonBlue
                        coolantTemp > 100 -> NeonRed
                        coolantTemp > 90 -> NeonAmber
                        else -> NeonGreen
                    }
                    SecondaryMetric(
                        title = "TEMP",
                        value = coolantTemp,
                        unit = "°C",
                        pid = ObdPid.COOLANT_TEMP,
                        color = tempColor,
                        isAnomalous = tempAnomaly,
                        delay = 50
                    )
                }
                item {
                    SecondaryMetric(
                        title = "THROTTLE",
                        value = throttlePos,
                        unit = "%",
                        pid = ObdPid.THROTTLE_POS,
                        color = NeonCyan,
                        isAnomalous = throttleAnomaly,
                        delay = 100
                    )
                }
                item {
                    SecondaryMetric(
                        title = "LOAD",
                        value = engineLoad,
                        unit = "%",
                        pid = ObdPid.ENGINE_LOAD,
                        color = NeonAmber,
                        isAnomalous = loadAnomaly,
                        delay = 150
                    )
                }
                item {
                    SecondaryMetric(
                        title = "MAF",
                        value = mafRate,
                        unit = "g/s",
                        pid = ObdPid.MAF_RATE,
                        color = TextPrimary,
                        isAnomalous = mafAnomaly,
                        delay = 200
                    )
                }
            }
        }

        // Botón REC Flotante
        RecordingButton(
            state = recordingState,
            onClick = { viewModel.toggleRecording() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        )
    }
}

@Composable
fun RecordingButton(
    state: RecordingState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    LargeFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = when (state) {
            RecordingState.RECORDING -> NeonRed.copy(alpha = 0.2f)
            RecordingState.SAVED -> NeonGreen.copy(alpha = 0.2f)
            else -> DeepSurface
        },
        contentColor = when (state) {
            RecordingState.RECORDING -> NeonRed
            RecordingState.SAVED -> NeonGreen
            else -> TextPrimary
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when (state) {
                    RecordingState.RECORDING -> Icons.Default.FiberManualRecord
                    RecordingState.SAVED -> Icons.Default.Save
                    else -> Icons.Default.FiberManualRecord
                },
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .then(if (state == RecordingState.RECORDING) Modifier.scale(alpha) else Modifier)
            )
            Text(
                text = when (state) {
                    RecordingState.RECORDING -> "REC"
                    RecordingState.SAVED -> "SAVED"
                    else -> "START"
                },
                style = FuturisticTypography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AnomalyGaugeWrapper(
    isAnomalous: Boolean,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "anomaly")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isAnomalous) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, NeonRed.copy(alpha = alpha), CircleShape)
            )
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = NeonRed,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            )
        }
        content()
    }
}

@Composable
fun SecondaryMetric(
    title: String,
    value: Float,
    unit: String,
    pid: ObdPid,
    color: Color,
    isAnomalous: Boolean,
    delay: Long
) {
    MetricCard(
        delayMillis = delay,
        accentColor = if (isAnomalous) NeonRed else color
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = FuturisticTypography.labelSmall,
                    color = if (isAnomalous) NeonRed else TextMuted
                )
                if (isAnomalous) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Warning, null, tint = NeonRed, modifier = Modifier.size(12.dp))
                }
            }

            val animatedValue by animateFloatAsState(
                targetValue = value,
                animationSpec = tween(durationMillis = 500),
                label = "metricAnim"
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value == 0f) {
                    Box(modifier = Modifier.fillMaxSize().clip(CircleShape).shimmerEffect())
                } else {
                    GaugeArc(
                        value = animatedValue,
                        minValue = pid.minValue,
                        maxValue = pid.maxValue,
                        activeColor = if (isAnomalous) NeonRed else color,
                        strokeWidth = 15f
                    )
                    Text(
                        text = String.format("%.0f", animatedValue),
                        style = FuturisticTypography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = if (isAnomalous) NeonRed else TextPrimary
                    )
                }
            }

            Text(
                text = unit,
                style = FuturisticTypography.labelSmall,
                color = TextSecondary
            )
        }
    }
}
