package com.obelus.ui.ddt4all

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Ddt4allEcuListScreen(
    viewModel: Ddt4allViewModel = hiltViewModel(),
    onNavigateToDetail: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onBack: () -> Unit
) {
    val ecuList by viewModel.ecuList.collectAsState()
    val foundEcus by viewModel.foundEcus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isAutoScanning by viewModel.isAutoScanning.collectAsState()
    val healthSummary by viewModel.healthSummary.collectAsState()
    val pdfReportFile by viewModel.pdfReportFile.collectAsState()
    val showClearDtcConfirm by viewModel.showClearDtcConfirm.collectAsState()
    
    val context = LocalContext.current

    LaunchedEffect(pdfReportFile) {
        pdfReportFile?.let { file ->
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir Informe de Salud"))
                viewModel.clearPdfReport()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showClearDtcConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelClearDtc() },
            title = { Text("BORRAR TODOS LOS DTCs", color = NeonRed, style = FuturisticTypography.titleMedium) },
            text = { Text("Esta acción borrará los errores almacenados en todos los módulos. ¿Deseas continuar?", color = TextPrimary) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmClearAllDtcs() }) {
                    Text("BORRAR", color = NeonRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelClearDtc() }) {
                    Text("CANCELAR", color = TextSecondary)
                }
            },
            containerColor = DarkBackground
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SYSTEM EXPLORER", style = FuturisticTypography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (isAutoScanning) RadarAnimation(NeonAmber)
                    ActionButton(
                        label = if (isAutoScanning) "SCANNING" else "AUTO-SCAN",
                        icon = Icons.Default.Radar,
                        color = if (isAutoScanning) NeonAmber else NeonCyan,
                        onClick = { viewModel.startAutoScan() }
                    )
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ActionButton(
                        label = "DIAGNOSE",
                        icon = Icons.Default.Assignment,
                        color = NeonGreen,
                        onClick = onNavigateToDiagnostics
                    )
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ActionButton(
                        label = "CODING",
                        icon = Icons.Default.LockOpen,
                        color = NeonAmber,
                        onClick = onNavigateToSecurity
                    )
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ActionButton(
                        label = "CLEAR DTC",
                        icon = Icons.Default.DeleteForever,
                        color = NeonRed,
                        onClick = { viewModel.requestClearDtc() }
                    )
                }
            }

            healthSummary?.let { summary ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.05f)),
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("VEHICLE FINGERPRINT: READY", color = NeonGreen, style = FuturisticTypography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("VIN: ${summary.fingerprint.vin}", color = TextSecondary, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            Text("Battery: ${String.format(Locale.US, "%.2fV", summary.batteryVoltage)}", color = TextSecondary, fontSize = 10.sp)
                        }
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = NeonGreen)
                        }
                    }
                }
            }

            Text(
                text = if (foundEcus.isNotEmpty()) "IDENTIFIED SYSTEMS" else "AVAILABLE DEFINITIONS",
                style = FuturisticTypography.labelSmall,
                color = if (foundEcus.isNotEmpty()) NeonGreen else TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val displayList = if (foundEcus.isNotEmpty()) foundEcus else ecuList

            if (isLoading && !isAutoScanning && healthSummary == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(displayList) { ecu ->
                        Ddt4allEcuListItem(ecu) {
                            viewModel.selectEcu(ecu)
                            onNavigateToDetail()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(85.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.08f)),
        contentPadding = PaddingValues(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = color, letterSpacing = 0.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun RadarAnimation(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(85.dp)
            .scale(scale)
            .border(1.dp, color.copy(alpha = alpha), RoundedCornerShape(12.dp))
    )
}
