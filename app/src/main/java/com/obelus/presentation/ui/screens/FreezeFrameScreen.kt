package com.obelus.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.presentation.viewmodel.DiagnosticViewModel
import com.obelus.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezeFrameScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val freezeFrame = uiState.selectedFreezeFrame

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FREEZE FRAME", style = FuturisticTypography.titleMedium)
                        Text(
                            text = freezeFrame?.dtcCode ?: "---",
                            style = FuturisticTypography.labelSmall,
                            color = NeonCyan
                        )
                    }
                },
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(padding)
        ) {
            if (freezeFrame == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DeepSurface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AcUnit, contentDescription = null, tint = NeonCyan)
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    "Datos capturados por la ECU en el momento de la falla.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    items(freezeFrame.sensorValues.toList()) { (pid, value) ->
                        FreezeFrameItem(pid, value)
                    }
                }
            }
        }
    }
}

@Composable
fun FreezeFrameItem(pid: String, value: Double) {
    val isWarning = (pid == "COOLANT_TEMP" && value > 100) || (pid == "ENGINE_LOAD" && value > 80)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DeepSurface,
        shape = MaterialTheme.shapes.medium,
        border = if (isWarning) androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = pid.replace("_", " "),
                    style = FuturisticTypography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = String.format(Locale.US, "%.1f", value),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isWarning) NeonRed else TextPrimary
                )
            }
            if (isWarning) {
                Text(
                    "CRÍTICO",
                    color = NeonRed,
                    style = FuturisticTypography.labelSmall,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
