package com.obelus.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.obelusscan.data.local.SettingsDataStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorScreen(
    dataStore: SettingsDataStore,
    onBack: () -> Unit
) {
    val themeMode by dataStore.themeMode.collectAsState(initial = "system")
    val scope = rememberCoroutineScope()

    val themes = listOf(
        ThemeOption("system", "Default Material", Color(0xFF1E1E1E), Color(0xFF00E5FF)), // NeonCyan
        ThemeOption("dark", "Modo Oscuro Clásico", Color(0xFF2C2C2C), Color(0xFF42A5F5)),
        ThemeOption("oled", "OLED Pure Black", Color.Black, Color(0xFF00E5FF)),
        ThemeOption("cyber", "Cyberpunk Neon", Color(0xFF050510), Color(0xFFFF00FF)),
        ThemeOption("sport", "Sport Red Carbon", Color(0xFF1A1515), Color(0xFFFF3333)) // RaceAccent
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TEMA DE INTERFAZ", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            themes.forEach { option ->
                val isSelected = themeMode == option.id
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(option.bgColor)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) option.primaryColor else Color.Gray.copy(alpha=0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { scope.launch { dataStore.setTheme(option.id) } }
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = option.name,
                            color = if (option.id == "oled") Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(option.primaryColor))
                            Spacer(Modifier.width(8.dp))
                            Text("Primary Accent", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    if (isSelected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = option.primaryColor)
                    }
                }
            }
        }
    }
}

data class ThemeOption(val id: String, val name: String, val bgColor: Color, val primaryColor: Color)
