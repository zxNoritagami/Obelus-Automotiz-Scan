package com.obelus.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.local.entity.RaceRecord
import com.obelus.presentation.viewmodel.RaceHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaceHistoryScreen(
    viewModel: RaceHistoryViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val records   by viewModel.records.collectAsState()
    val bestTimes by viewModel.bestByType.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Carreras") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0D0D),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No hay carreras guardadas",
                        color = Color.Gray,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Completa una carrera para verla aquí",
                        color = Color.Gray.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Group by race type for section headers
                val grouped = records.groupBy { it.raceType }
                grouped.forEach { (type, typeRecords) ->
                    item {
                        Text(
                            text = type.displayName(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    val bestForType = bestTimes[type]?.finalTimeSeconds
                    items(typeRecords) { record ->
                        RaceHistoryItem(
                            record      = record,
                            personalBestTime = bestForType
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RaceHistoryItem(
    record: RaceRecord,
    personalBestTime: Float?
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault()) }
    val date       = dateFormat.format(Date(record.startTime))

    val isPB    = record.isPersonalBest
    val isRef   = record.isReference
    val delta   = if (personalBestTime != null && personalBestTime > 0f) {
        record.finalTimeSeconds - personalBestTime
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPB) Color(0xFF0D2A0D) else Color(0xFF1E1E1E)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: date + badges
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPB) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = "Personal Best",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (isRef) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Referencia",
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = date,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${record.targetSpeedStart} → ${record.targetSpeedEnd} km/h",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                if (record.estimatedHp > 0f) {
                    Text(
                        text = "Est. ${record.estimatedHp.toInt()} HP  •  Max ${String.format(Locale.US, "%.2f", record.maxGForce)} G",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            // Right: time + delta
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "%.3f s", record.finalTimeSeconds),
                    color = if (isPB) Color(0xFFFFD700) else Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (delta != null && !isPB) {
                    val deltaStr = String.format(Locale.US, "%+.3f s", delta)
                    Text(
                        text = deltaStr,
                        color = if (delta <= 0f) Color(0xFF66FF66) else Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (isPB) {
                    Text("🏆 PR", color = Color(0xFFFFD700), fontSize = 12.sp)
                }
            }
        }
    }
}

/** Extension to get human-readable display name from the stored type string */
fun String.displayName(): String = when (this) {
    "ACCELERATION_0_100" -> "Aceleración 0 → 100 km/h"
    "ACCELERATION_0_200" -> "Aceleración 0 → 200 km/h"
    "BRAKING_100_0"      -> "Frenada 100 → 0 km/h"
    else                 -> "Carrera personalizada"
}
