package com.obelus.ui.screens.actuators

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obelus.ui.components.actuators.ActuatorCard

private val BgDark      = Color(0xFF0D1117)
private val TextPrimary = Color(0xFFE6EDF3)

data class TestCategory(val id: Int, val name: String, val icon: ImageVector, val desc: String, val isCritical: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActuatorTestsScreen(
    onNavigateToTest: (Int) -> Unit,
    onBack: () -> Unit
) {
    val categories = listOf(
        TestCategory(1, "Sistema de Inyección", Icons.Default.LocalGasStation, "Corte de cilindros, bombas de combustible", isCritical = true),
        TestCategory(2, "Refrigeración del Motor", Icons.Default.AcUnit, "Electroventiladores (Baja/Alta), termostato"),
        TestCategory(3, "Control de Emisiones", Icons.Default.Co2, "Válvula EVAP, EGR, calentador de Lambda"),
        TestCategory(4, "Sistema Eléctrico", Icons.Default.ElectricBolt, "Alternador, relés principales, luces"),
        TestCategory(5, "Transmisión (TCM)", Icons.Default.Settings, "Solenoides de cambio, bloqueo de TCC", isCritical = true),
        TestCategory(6, "Carrocería (BCM)", Icons.Default.DirectionsCar, "Bocina, limpiaparabrisas, seguros de puerta")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tests de Actuadores", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
            Surface(
                color = Color(0xFFF85149).copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = "Precaución", tint = Color(0xFFF85149))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "PRECAUCIÓN: Estas pruebas inyectan comandos activos en la ECU. Mantén el motor apagado con el switch en ON a menos que se indique lo contrario.",
                        color = Color(0xFFF85149),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(categories) { cat ->
                    ActuatorCard(
                        title = cat.name,
                        description = cat.desc,
                        icon = cat.icon,
                        isCritical = cat.isCritical,
                        onClick = { onNavigateToTest(cat.id) }
                    )
                }
            }
        }
    }
}
