package com.obelus.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxValue.*
import com.obelus.ui.components.history.HistoryFilterChip
import com.obelus.ui.components.history.HistoryItem
import com.obelus.ui.components.history.HistoryItemType
import com.obelus.data.local.entity.RaceRecord
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.presentation.viewmodel.HistoryViewModel

// ─────────────────────────────────────────────────────────────────────────────
// PALETA DE COLORES
// ─────────────────────────────────────────────────────────────────────────────
private val BgDark        = Color(0xFF0D1117)
private val TextPrimary   = Color(0xFFE6EDF3)
private val TextMuted     = Color(0xFF8B949E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateToDetail: (Long) -> Unit,
    onBack: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(HistoryItemType.RACE) }
    
    val races by viewModel.races.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Diagnóstico", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        ) {
            // Filtros horizontales
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                HistoryFilterChip(
                    label = "Carreras (Telemetría)",
                    selected = selectedFilter == HistoryItemType.RACE,
                    onClick = { selectedFilter = HistoryItemType.RACE }
                )
                HistoryFilterChip(
                    label = "DTC Borrados",
                    selected = selectedFilter == HistoryItemType.DTC,
                    onClick = { selectedFilter = HistoryItemType.DTC },
                    selectedColor = Color(0xFFF85149)
                )
            }

            // Lista Swipeable
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedFilter == HistoryItemType.RACE) {
                    items(races, key = { it.id }) { race ->
                        SwipeToDeleteContainer(
                            item = race,
                            onDelete = { viewModel.deleteRace(it.id) }
                        ) {
                            HistoryItem(
                                title = race.raceType,
                                subtitle = "Hace ${((System.currentTimeMillis() - race.startTime) / 86400000)} días • Top: ${race.targetSpeedEnd} km/h",
                                value = String.format("%.2fs", race.finalTimeSeconds),
                                type = HistoryItemType.RACE,
                                isPr = race.isPersonalBest,
                                onClick = { onNavigateToDetail(race.id) }
                            )
                        }
                    }
                } else if (selectedFilter == HistoryItemType.DTC) {
                    item {
                        Text("No hay registros de códigos limpios aún.", color = TextMuted, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SwipeToDeleteContainer(
    item: T,
    onDelete: (T) -> Unit,
    animationDuration: Int = 500,
    content: @Composable (T) -> Unit
) {
    var isRemoved by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == EndToStart) {
                isRemoved = true
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(key1 = isRemoved) {
        if(isRemoved) {
            kotlinx.coroutines.delay(animationDuration.toLong())
            onDelete(item)
        }
    }

    AnimatedVisibility(
        visible = !isRemoved,
        exit = androidx.compose.animation.shrinkVertically(
            animationSpec = androidx.compose.animation.core.tween(durationMillis = animationDuration),
            shrinkTowards = Alignment.Top
        ) + androidx.compose.animation.fadeOut()
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val color = if (direction == EndToStart) Color(0xFFF85149) else Color.Transparent
                val alignment = Alignment.CenterEnd

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(color, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    if (direction == EndToStart) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.White)
                    }
                }
            },
            content = { content(item) },
            enableDismissFromStartToEnd = false
        )
    }
}
