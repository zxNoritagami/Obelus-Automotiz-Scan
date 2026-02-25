package com.obelus.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.obelusscan.data.local.SettingsDataStore
import com.obelus.ui.theme.NeonCyan
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettings(
    dataStore: SettingsDataStore,
    onBack: () -> Unit
) {
    val preferredObdDevice by dataStore.preferredObdDevice.collectAsState(initial = "AUTO")
    val scope = rememberCoroutineScope()

    val options = listOf(
        Triple("AUTO", "Auto Detect (Recomendado)", Icons.Default.Bluetooth),
        Triple("BLUETOOTH", "ELM327 Bluetooth/BLE", Icons.Default.Bluetooth),
        Triple("WIFI", "ELM327 Wi-Fi (IP:Port)", Icons.Default.Wifi),
        Triple("USB", "OpenDiag USB K-Line/CAN", Icons.Default.Usb)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CONECTIVIDAD OBD", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            options.forEach { (id, name, icon) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { dataStore.setPreferredObdDevice(id) } }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = if (preferredObdDevice == id) NeonCyan else Color.Gray)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = name,
                        color = if (preferredObdDevice == id) NeonCyan else MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = if (preferredObdDevice == id) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    RadioButton(
                        selected = preferredObdDevice == id,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = NeonCyan, unselectedColor = Color.Gray)
                    )
                }
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
            }
        }
    }
}
