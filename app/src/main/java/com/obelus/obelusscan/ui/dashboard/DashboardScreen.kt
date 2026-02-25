package com.obelus.obelusscan.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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

    LaunchedEffect(Unit) {
        viewModel.startScanning()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(top = 8.dp, start = 12.dp, end = 12.dp)
    ) {
        // Status Bar
        StatusBar(isConnected = true, modifier = Modifier.padding(bottom = 16.dp))

        // Main Metric (RPM & Speed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main Gauge: RPM
            Box(
                modifier = Modifier
                    .weight(0.6f) // 60% of Width
                    .padding(end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Color dynamico RPM: Cyan -> Amarillo -> Rojo
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

                if (rpm == 0f) { // Simula estado "Loading" o desconectado
                    Box(modifier = Modifier.size(200.dp).clip(CircleShape).shimmerEffect())
                } else {
                    GaugeArc(
                        value = animatedRpm,
                        minValue = ObdPid.RPM.minValue,
                        maxValue = ObdPid.RPM.maxValue,
                        activeColor = rpmColor,
                        strokeWidth = 35f
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.0f", rpm),
                        style = FuturisticTypography.headlineLarge,
                        color = rpmColor
                    )
                    Text(
                        text = "RPM",
                        style = FuturisticTypography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            // Secondary Vital: Speed (Digital Style)
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

        // Secondary Metrics Grid 2x2
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Coolant Temp
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
                    delay = 50
                )
            }
            // Throttle
            item {
                SecondaryMetric(
                    title = "THROTTLE",
                    value = throttlePos,
                    unit = "%",
                    pid = ObdPid.THROTTLE_POS,
                    color = NeonCyan,
                    delay = 100
                )
            }
            // Engine Load
            item {
                SecondaryMetric(
                    title = "LOAD",
                    value = engineLoad,
                    unit = "%",
                    pid = ObdPid.ENGINE_LOAD,
                    color = NeonAmber,
                    delay = 150
                )
            }
            // MAF Rate
            item {
                SecondaryMetric(
                    title = "MAF",
                    value = mafRate,
                    unit = "g/s",
                    pid = ObdPid.MAF_RATE,
                    color = TextPrimary,
                    delay = 200
                )
            }
        }
    }
}

@Composable
fun SecondaryMetric(
    title: String,
    value: Float,
    unit: String,
    pid: ObdPid,
    color: Color,
    delay: Long
) {
    MetricCard(
        delayMillis = delay,
        accentColor = color
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = FuturisticTypography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )

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
                        activeColor = color,
                        strokeWidth = 15f
                    )
                    Text(
                        text = String.format("%.0f", animatedValue),
                        style = FuturisticTypography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = TextPrimary
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

