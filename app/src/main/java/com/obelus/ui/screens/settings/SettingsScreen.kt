package com.obelus.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.obelus.obelusscan.data.local.SettingsDataStore
import com.obelus.ui.components.settings.SettingGroup
import com.obelus.ui.components.settings.SettingItem
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.obelusscan.ui.settings.SettingsViewModel
import androidx.compose.ui.platform.LocalClipboardManager
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dataStore: SettingsDataStore,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToTheme: () -> Unit,
    onNavigateToConnection: () -> Unit,
    onNavigateToCrashLogs: () -> Unit,
    onBack: () -> Unit
) {
    val themeMode by dataStore.themeMode.collectAsState(initial = "system")
    val preferredObdDevice by dataStore.preferredObdDevice.collectAsState(initial = "AUTO")
    val unitsConfig by dataStore.unitsConfig.collectAsState(initial = null)
    
    val mechanicName by viewModel.mechanicName.collectAsState()
    val remainingMinutes by viewModel.remainingMinutes.collectAsState()
    
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshOtpRemainingTime()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CONFIGURACIÓN", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                SettingGroup(title = "Apariencia") {
                    SettingItem(
                        icon = Icons.Default.Palette,
                        title = "Tema Interfaz",
                        subtitle = "Personaliza los colores del escáner",
                        value = themeMode.uppercase(),
                        onClick = onNavigateToTheme
                    )
                }
            }

            item {
                SettingGroup(title = "Conexión OBD") {
                    SettingItem(
                        icon = Icons.Default.BluetoothConnected,
                        title = "Dispositivo y Protocolo",
                        subtitle = "Preferencias de conexión y hardware",
                        value = preferredObdDevice,
                        onClick = onNavigateToConnection
                    )
                }
            }

            item {
                SettingGroup(title = "Unidades de Medida") {
                    SettingItem(
                        icon = Icons.Default.Speed,
                        title = "Velocidad y Distancia",
                        subtitle = "Selecciona km/h o mph",
                        value = unitsConfig?.distanceUnit?.uppercase() ?: "KM",
                        onClick = { /* TODO: Add dialog or expansion */ }
                    )
                }
            }

            item {
                SettingGroup(title = "Sistema & Telemetría") {
                    SettingItem(
                        icon = Icons.Default.BugReport,
                        title = "Reporte de Errores",
                        subtitle = "Ver crash logs recientes",
                        onClick = onNavigateToCrashLogs,
                        colorOverride = Color(0xFFFF5555)
                    )
                    SettingItem(
                        icon = Icons.Default.Info,
                        title = "Acerca de Scanner Pro",
                        subtitle = "Versión 2.0.0-cyber",
                        onClick = { /* TODO: About Dialog */ },
                        colorOverride = Color.Gray
                    )
                }
            }

            item {
                SettingGroup(title = "Perfil del Taller (Seguridad Web)") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = mechanicName,
                            onValueChange = { viewModel.setMechanicName(it) },
                            label = { Text("Nombre del Mecánico") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("Máximo 10 caracteres, solo letras (A-Z).") },
                            isError = mechanicName.length > 10 || mechanicName.any { !it.isLetter() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.copyPasswordToClipboard(clipboardManager) {
                                    Toast.makeText(context, "Contraseña copiada - Válida 60 min", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBC8CFF))
                        ) {
                            Icon(Icons.Default.VpnKey, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generar y Copiar Contraseña Web", fontWeight = FontWeight.Bold)
                        }
                        
                        Text(
                            text = "Válida por 60 minutos. Múltiples dispositivos permitidos.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        if (remainingMinutes > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⏱️ Expira en $remainingMinutes min",
                                    color = Color(0xFF00FF88),
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(onClick = { viewModel.invalidatePassword() }) {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Forzar nueva")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
