package com.obelus.ui.ddt4all

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.domain.model.DdtCommand
import com.obelus.domain.model.DdtEcu
import com.obelus.presentation.ui.components.DiagnosticScope
import com.obelus.ui.components.dashboard.MetricCard
import com.obelus.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Ddt4allEcuDetailScreen(
    viewModel: Ddt4allViewModel,
    onBack: () -> Unit
) {
    val selectedEcu by viewModel.selectedEcu.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val parameterValues by viewModel.parameterValues.collectAsState()
    val parameterHistory by viewModel.parameterHistory.collectAsState()
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var expandedParamName by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(selectedEcu?.name ?: "ECU CONSOLE", style = FuturisticTypography.titleMedium) },
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
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Black,
                contentColor = NeonCyan,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = NeonCyan
                        )
                    }
                }
            ) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) {
                    Text("LIVE DATA", modifier = Modifier.padding(16.dp), style = FuturisticTypography.labelSmall)
                }
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                    Text("COMMANDS", modifier = Modifier.padding(16.dp), style = FuturisticTypography.labelSmall)
                }
                Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }) {
                    Text("SYSTEM ID", modifier = Modifier.padding(16.dp), style = FuturisticTypography.labelSmall)
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> LiveDataTab(
                        ecu = selectedEcu,
                        isScanning = isScanning,
                        values = parameterValues,
                        history = parameterHistory,
                        expandedParam = expandedParamName,
                        onParamClick = { expandedParamName = if (expandedParamName == it) null else it },
                        onToggleScan = { viewModel.toggleLiveScan() }
                    )
                    1 -> CommandsTab(selectedEcu?.commands ?: emptyList()) { viewModel.executeCommand(it) }
                    2 -> SystemIdTab(selectedEcu)
                }
            }
        }
    }
}

@Composable
fun LiveDataTab(
    ecu: DdtEcu?,
    isScanning: Boolean,
    values: Map<String, String>,
    history: Map<String, List<Float>>,
    expandedParam: String?,
    onParamClick: (String) -> Unit,
    onToggleScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SENSORS DETECTED: ${ecu?.parameters?.size ?: 0}", color = TextSecondary, style = FuturisticTypography.labelSmall)
            Button(
                onClick = onToggleScan,
                colors = ButtonDefaults.buttonColors(containerColor = if (isScanning) NeonRed else NeonGreen)
            ) {
                Icon(if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isScanning) "STOP" else "READ ALL")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val params = ecu?.parameters ?: emptyList()
            items(params) { param ->
                val value = values[param.name] ?: "--"
                val isExpanded = expandedParam == param.name
                
                ExpertParameterCard(
                    name = param.name,
                    value = value,
                    unit = param.unit,
                    accentColor = NeonCyan,
                    isExpanded = isExpanded,
                    history = history[param.name] ?: emptyList(),
                    maxValue = param.maxValue,
                    onClick = { onParamClick(param.name) }
                )
            }
        }
    }
}

@Composable
fun ExpertParameterCard(
    name: String, 
    value: String, 
    unit: String, 
    accentColor: Color,
    isExpanded: Boolean,
    history: List<Float>,
    maxValue: Float,
    onClick: () -> Unit
) {
    MetricCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        accentColor = if (isExpanded) accentColor else Color.Transparent
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name.uppercase(), style = FuturisticTypography.labelSmall, color = TextSecondary)
                    Text(text = value, style = FuturisticTypography.headlineMedium, color = TextPrimary)
                }
                if (unit.isNotEmpty()) {
                    Text(unit, color = accentColor, style = FuturisticTypography.labelMedium)
                }
            }
            
            if (isExpanded) {
                Spacer(Modifier.height(16.dp))
                DiagnosticScope(
                    dataPoints = history,
                    maxValue = if (maxValue > 0) maxValue else 100f,
                    lineColor = accentColor
                )
            }
        }
    }
}

@Composable
fun CommandsTab(commands: List<DdtCommand>, onExecute: (DdtCommand) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(commands) { cmd ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cmd.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(cmd.description, color = TextSecondary, fontSize = 12.sp)
                        Text("HEX: ${cmd.hexRequest}", color = NeonAmber, fontSize = 10.sp, style = FuturisticTypography.labelSmall)
                    }
                    IconButton(onClick = { onExecute(cmd) }) {
                        Icon(Icons.Default.Send, contentDescription = "Run", tint = NeonCyan)
                    }
                }
            }
        }
    }
}

@Composable
fun SystemIdTab(ecu: DdtEcu?) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("ECU HARDWARE INFO", color = NeonCyan, style = FuturisticTypography.titleMedium)
        Spacer(Modifier.height(16.dp))
        InfoRow("Hardware Name", ecu?.name ?: "Unknown")
        InfoRow("Diagnostic Protocol", ecu?.protocol ?: "Unknown")
        InfoRow("Functional Group", ecu?.group ?: "Unknown")
        Spacer(Modifier.height(32.dp))
        Text("DATABASE SOURCE", color = NeonAmber, style = FuturisticTypography.labelSmall)
        Text("Definition: sample_ems3120.xml", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, style = FuturisticTypography.bodyMedium)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}
