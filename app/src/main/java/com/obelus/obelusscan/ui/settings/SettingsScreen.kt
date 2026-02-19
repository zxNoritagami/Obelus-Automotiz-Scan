package com.obelus.obelusscan.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val theme by viewModel.theme.collectAsState()
    val units by viewModel.units.collectAsState()
    val refreshRate by viewModel.refreshRate.collectAsState()
    val vehicleWeight by viewModel.vehicleWeight.collectAsState()
    val telemetryConfig by viewModel.telemetryConfig.collectAsState()
    val testResult by viewModel.testConnectionResult.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()
    val context = LocalContext.current

    // Toast cuando llega el resultado del test
    LaunchedEffect(testResult) {
        testResult?.let { ok ->
            val msg = if (ok) "✅ Conexión exitosa al broker" else "❌ No se pudo conectar al broker"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearTestResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection("Apariencia") {
                ThemeSelector(currentTheme = theme, onSelect = viewModel::setTheme)
            }

            SettingsSection("Unidades") {
                UnitSelector(
                    title = "Distancia",
                    icon = Icons.Default.Straighten,
                    current = units.distanceUnit,
                    options = listOf("km" to "Kilómetros (km)", "miles" to "Millas (mi)"),
                    onSelect = viewModel::setDistanceUnit
                )
                Spacer(modifier = Modifier.height(16.dp))
                UnitSelector(
                    title = "Consumo",
                    icon = Icons.Default.Speed,
                    current = units.consumptionUnit,
                    options = listOf(
                        "l_100km" to "Litros/100km",
                        "mpg_us" to "MPG (US)",
                        "mpg_uk" to "MPG (UK)"
                    ),
                    onSelect = viewModel::setConsumptionUnit
                )
            }

            SettingsSection("Avanzado") {
                RefreshRateSelector(
                    currentMs = refreshRate,
                    onValueChange = viewModel::setRefreshRate
                )
            }

            // ── Sección Telemetría MQTT ────────────────────────────────────
            var brokerUrlInput by remember(telemetryConfig.brokerUrl) {
                mutableStateOf(telemetryConfig.brokerUrl)
            }
            val isBrokerUrlValid = brokerUrlInput.startsWith("tcp://") ||
                                   brokerUrlInput.startsWith("ssl://")

            SettingsSection("Telemetría MQTT") {
                // Switch ON/OFF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (telemetryConfig.isTelemetryEnabled) Icons.Default.Cloud
                            else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (telemetryConfig.isTelemetryEnabled)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Publicar telemetría", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (telemetryConfig.isTelemetryEnabled) "Activa • cada 5s"
                                else "Desactivada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = telemetryConfig.isTelemetryEnabled,
                        onCheckedChange = viewModel::setTelemetryEnabled
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Campo URL del broker
                OutlinedTextField(
                    value = brokerUrlInput,
                    onValueChange = { brokerUrlInput = it },
                    label = { Text("URL del Broker") },
                    placeholder = { Text("tcp://broker.hivemq.com:1883") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = brokerUrlInput.isNotEmpty() && !isBrokerUrlValid,
                    supportingText = {
                        if (brokerUrlInput.isNotEmpty() && !isBrokerUrlValid) {
                            Text(
                                "Formato inválido. Usa tcp:// o ssl://",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                "ID cliente: ${telemetryConfig.clientId}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = {
                        if (brokerUrlInput != telemetryConfig.brokerUrl && isBrokerUrlValid) {
                            TextButton(
                                onClick = { viewModel.setBrokerUrl(brokerUrlInput) }
                            ) { Text("Guardar") }
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Botón Probar Conexión
                Button(
                    onClick = { viewModel.testConnection(brokerUrlInput) },
                    enabled = isBrokerUrlValid && !isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Probando...")
                    } else {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Probar conexión")
                    }
                }
            }
            // ── Fin Telemetría ───────────────────────────────────────────────

            // ── Sección Race Mode ────────────────────────────────────────────
            SettingsSection("Modo Race / Rendimiento") {
                VehicleWeightSelector(
                    currentKg     = vehicleWeight,
                    onValueChange = viewModel::setVehicleWeight
                )
            }
            // ── Fin Race Mode ────────────────────────────────────────────────

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.restoreDefaults() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restaurar valores por defecto")
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun ThemeSelector(currentTheme: String, onSelect: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text("Tema de la App", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ThemeOption("Claro", "light", currentTheme, onSelect)
            ThemeOption("Oscuro", "dark", currentTheme, onSelect)
            ThemeOption("Sistema", "system", currentTheme, onSelect)
        }
    }
}

@Composable
fun ThemeOption(label: String, value: String, current: String, onSelect: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = (value == current),
            onClick = { onSelect(value) }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitSelector(
    title: String,
    icon: ImageVector,
    current: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.find { it.first == current }?.second ?: current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .width(180.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RefreshRateSelector(currentMs: Int, onValueChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Frecuencia de actualización", style = MaterialTheme.typography.bodyLarge)
            Text("${currentMs}ms", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = currentMs.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 200f..2000f,
            steps = 17,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Valores más bajos consumen más batería pero dan datos más fluidos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun VehicleWeightSelector(currentKg: Int, onValueChange: (Int) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("Peso del vehículo", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Necesario para estimar HP en Race Mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$currentKg kg",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = currentKg.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 800f..3000f,
            steps = 43, // every 50 kg
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Rango: 800 – 3000 kg. Valor típico: 1000–1500 kg (auto), 1800–2500 kg (SUV/truck).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
