package com.obelus.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.webserver.WebServerState
import com.obelus.obelusscan.ui.theme.*
import com.obelus.presentation.viewmodel.WebServerViewModel

// ─────────────────────────────────────────────────────────────────────────────
// PALETA DE COLORES (Unificada con LogViewer)
// ─────────────────────────────────────────────────────────────────────────────
private val BgDark        = Color(0xFF0D1117)
private val BgPanel       = Color(0xFF161B22)
private val AccentBlue    = Color(0xFF58A6FF)
private val AccentPurple  = Color(0xFFBC8CFF)
private val TextPrimary   = Color(0xFFE6EDF3)
private val TextMuted     = Color(0xFF8B949E)
private val AccentRed     = Color(0xFFF85149)
private val DividerColor  = Color(0xFF30363D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebServerScreen(
    viewModel: WebServerViewModel = hiltViewModel()
) {
    val state by viewModel.serverState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard Web", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // SECCIÓN A: CONTROL
            ServerControlCard(
                isRunning = state is WebServerState.Running,
                error = (state as? WebServerState.Error)?.message,
                onToggle = viewModel::toggleServer,
                onRefresh = { viewModel.toggleServer(); viewModel.toggleServer() } // Re-trigger detection
            )

            Spacer(Modifier.height(24.dp))

            // SECCIÓN B: URL (Solo si running)
            AnimatedVisibility(
                visible = state is WebServerState.Running,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val url = (state as? WebServerState.Running)?.url ?: ""
                UrlDisplayCard(
                    url = url,
                    onCopy = {
                        viewModel.copyUrlToClipboard(url)
                        // Snackbar (impl simplificada)
                    },
                    onShare = { viewModel.shareUrl(url) },
                    onOpen = { viewModel.openLocally(url) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // SECCIÓN C: GUÍA VISUAL
            ConnectionGuideCard(isRunning = state is WebServerState.Running)
            
            if (state is WebServerState.Error) {
                Text(
                    text = (state as WebServerState.Error).message,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ServerControlCard(
    isRunning: Boolean, 
    error: String?,
    onToggle: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgPanel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRunning) "Servidor Activo" else "Servidor Inactivo",
                        color = if (isRunning) Color(0xFF00FF88) else TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (isRunning) "Transmitiendo PIDs en tiempo real" else "Toca para iniciar el servidor web",
                        color = if (error != null) AccentRed else TextMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isRunning,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentPurple,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = BgDark
                    )
                )
            }
            
            if (error != null || !isRunning) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refrescar Red", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun UrlDisplayCard(url: String, onCopy: () -> Unit, onShare: () -> Unit, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.1f)),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentPurple.copy(alpha = 0.5f))),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Tu dirección local:", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                url,
                color = AccentPurple,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copiar")
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enviar")
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Icon(Icons.Default.Language, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Abrir en este dispositivo")
            }
        }
    }
}

@Composable
private fun ConnectionGuideCard(isRunning: Boolean) {
    var expanded by remember { mutableStateOf(true) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgPanel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HelpOutline, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("¿Cómo conectarse?", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextMuted)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    GuideItem("1", "Misma red", "Asegúrate que tu PC/Tablet esté en el mismo WiFi.")
                    GuideItem("2", "Copia el link", "Usa el botón copiar o enviar para llevar la URL al otro equipo.")
                    GuideItem("3", "Navegador", "Abre Chrome o Safari y pega el link en la barra de direcciones.")
                    
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QrCode2, null, modifier = Modifier.size(40.dp), tint = TextMuted)
                            Text("Código QR autogenerado", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideItem(number: String, title: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(AccentBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, color = TextMuted, fontSize = 12.sp)
        }
    }
}
