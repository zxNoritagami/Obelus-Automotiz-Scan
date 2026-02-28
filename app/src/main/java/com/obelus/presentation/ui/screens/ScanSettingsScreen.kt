package com.obelus.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.BuildConfig
import com.obelus.presentation.ui.components.SectionDividerHeader

// ─────────────────────────────────────────────────────────────────────────────
// ScanSettingsScreen.kt
// Configuración básica del módulo de diagnóstico OBD2.
// Complementa el SettingsScreen existente (en com.obelus.ui.screens.settings).
// ─────────────────────────────────────────────────────────────────────────────

enum class UnitSystem { METRIC, IMPERIAL }

/**
 * Pantalla de ajustes del módulo de escaneo.
 *
 * @param onBack    Callback al pulsar la flecha de retroceso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSettingsScreen(
    onBack: () -> Unit = {}
) {
    // Estado local (en un ViewModel real se persistiría en DataStore)
    var darkMode    by remember { mutableStateOf(true) }
    var unitSystem  by remember { mutableStateOf(UnitSystem.METRIC) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ajustes de Escaneo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            // ── Apariencia ─────────────────────────────────────────────────
            item { SectionDividerHeader(title = "Apariencia") }
            item {
                SettingsToggleItem(
                    icon    = Icons.Default.DarkMode,
                    title   = "Tema oscuro",
                    subtitle = "Optimizado para usar en taller (alto contraste)",
                    checked = darkMode,
                    onCheckedChange = { darkMode = it }
                )
            }

            // ── Unidades ───────────────────────────────────────────────────
            item { SectionDividerHeader(title = "Unidades de medida") }
            item {
                SettingsSelectorItem(
                    icon    = Icons.Default.Straighten,
                    title   = "Sistema de unidades",
                    options = listOf("Métrico (km/h, °C, kPa)" to UnitSystem.METRIC,
                                     "Imperial (mph, °F, psi)"  to UnitSystem.IMPERIAL),
                    selected = unitSystem,
                    onSelect = { unitSystem = it }
                )
            }

            // ── Datos ──────────────────────────────────────────────────────
            item { SectionDividerHeader(title = "Datos guardados") }
            item {
                SettingsActionItem(
                    icon     = Icons.Default.DeleteForever,
                    title    = "Borrar datos guardados",
                    subtitle = "Elimina sesiones, lecturas y DTCs históricos",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick  = { showDeleteDialog = true }
                )
            }

            // ── Acerca de ──────────────────────────────────────────────────
            item { SectionDividerHeader(title = "Acerca de") }
            item {
                SettingsInfoItem(
                    icon    = Icons.Default.Info,
                    title   = "Versión de la app",
                    value   = try { BuildConfig.VERSION_NAME } catch (e: Throwable) { "dev" }
                )
            }
            item {
                SettingsInfoItem(
                    icon    = Icons.Default.Code,
                    title   = "Build",
                    value   = try { BuildConfig.VERSION_CODE.toString() } catch (e: Throwable) { "—" }
                )
            }
            item {
                SettingsActionItem(
                    icon     = Icons.Default.OpenInBrowser,
                    title    = "Repositorio en GitHub",
                    subtitle = "github.com/NoritagamiAngelo/Obelus",
                    onClick  = {
                        uriHandler.openUri("https://github.com/NoritagamiAngelo/Obelus")
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon     = Icons.Default.BugReport,
                    title    = "Reportar un problema",
                    subtitle = "Abre el rastreador de issues en GitHub",
                    onClick  = {
                        uriHandler.openUri("https://github.com/NoritagamiAngelo/Obelus/issues")
                    }
                )
            }
        }
    }

    // ── Diálogo confirmar borrado ──────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(20.dp),
            icon  = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("¿Borrar todos los datos?", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "Esta acción eliminará permanentemente todas las sesiones, lecturas de señales, " +
                    "DTCs históricos y archivos DBC importados. No se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { /* viewModel.clearAllData() */ showDeleteDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape   = RoundedCornerShape(10.dp)
                ) { Text("Borrar todo") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ── Ítems de ajuste reutilizables ─────────────────────────────────────────────

@Composable
private fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape  = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun <T> SettingsSelectorItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onSelect: (T) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape  = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            options.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == value,
                        onClick  = { onSelect(value) }
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color   = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape  = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Icon(
                Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ScanSettingsScreen",
    widthDp = 390, heightDp = 780)
@Composable
private fun PreviewScanSettings() {
    MaterialTheme { ScanSettingsScreen() }
}
