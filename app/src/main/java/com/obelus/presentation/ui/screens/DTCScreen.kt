package com.obelus.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.repository.EnrichedDtc
import com.obelus.data.repository.SeverityLevel
import com.obelus.presentation.viewmodel.DTCViewModel
import com.obelus.ui.components.dtc.*
import com.obelus.ui.theme.DarkBackground
import com.obelus.ui.theme.DtcCritical
import com.obelus.ui.theme.NeonCyan

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DTCScreen(
    viewModel: DTCViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToDetail: (EnrichedDtc) -> Unit = {}
) {
    val enrichedDtcs by viewModel.enrichedDtcs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("DIAGNÓSTICO OBD", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("VIN Detectado: WBAXXXXXXXXXXXXX", color = NeonCyan, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { viewModel.readDTCs() }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Refrescar", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = enrichedDtcs.isNotEmpty() && !isScanning,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { showClearDialog = true },
                    containerColor = DtcCritical,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Borrar Códigos")
                }
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Empty State
            AnimatedVisibility(
                visible = enrichedDtcs.isEmpty() && !isScanning,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(300))
            ) {
                EmptyFuturisticState(modifier = Modifier.align(Alignment.Center))
            }

            // List of DTCs
            AnimatedVisibility(
                visible = enrichedDtcs.isNotEmpty() && !isScanning,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(300))
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Agrupar por sistema (Engine, Transmission, etc)
                    val grouped = enrichedDtcs.groupBy { it.systemLabel }
                    
                    grouped.forEach { (system, items) ->
                        stickyHeader {
                            Text(
                                text = system.uppercase(),
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBackground.copy(alpha = 0.9f))
                                    .padding(vertical = 8.dp)
                            )
                        }
                        
                        itemsIndexed(items, key = { _, dtcItem: EnrichedDtc -> dtcItem.dtc.code }) { index: Int, dtc: EnrichedDtc ->
                            val severityEnum = when (dtc.severityLevel) {
                                SeverityLevel.ERROR -> DtcSeverity.CRITICAL
                                SeverityLevel.WARNING -> DtcSeverity.WARNING
                                SeverityLevel.INFO -> DtcSeverity.INFO
                            }

                            var isVisible by remember { mutableStateOf(false) }
                            LaunchedEffect(dtc.dtc.code) {
                                kotlinx.coroutines.delay(index * 50L) // Stagger 50ms per item
                                isVisible = true
                            }
                            
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(tween(300)),
                                modifier = Modifier.animateItemPlacement()
                            ) {
                                DtcCard(
                                    code = dtc.dtc.code,
                                    description = dtc.descriptionEs,
                                    severity = severityEnum,
                                    ecuName = dtc.manufacturer,
                                    onClick = { onNavigateToDetail(dtc) }
                                )
                            }
                        }
                    }
                }
            }

            // Scanner Overlay effects
            VinScannerOverlay(isScanning = isScanning)

            if (showClearDialog) {
                ClearDtcDialog(
                    onConfirm = {
                        viewModel.clearDTCs()
                        showClearDialog = false
                    },
                    onDismiss = { showClearDialog = false }
                )
            }
        }
    }
}

@Composable
fun EmptyFuturisticState(modifier: Modifier = Modifier) {
    // Latido sutil verde indicando OK
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1500),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "okPulse"
    )

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircleOutline,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF00FF66).copy(alpha = alpha) // Neon Green OK
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "SISTEMA LIMPIO",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No se detectaron códigos de falla permanentes o erráticos en el último escaneo.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
