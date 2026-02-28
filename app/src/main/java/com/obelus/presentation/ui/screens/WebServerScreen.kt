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
import kotlinx.coroutines.delay

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
    viewModel: WebServerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.serverState.collectAsState()
    val mechanicName by viewModel.mechanicName.collectAsState()
    val generatedPassword by viewModel.generatedPassword.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard Web", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = TextPrimary)
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ServerControlCard(
                isRunning = state is WebServerState.Running,
                onToggle = viewModel::toggleServer
            )

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(visible = state is WebServerState.Running) {
                Column {
                    UrlDisplayCard(
                        url = (state as WebServerState.Running).url,
                        onCopy = { viewModel.copyUrlToClipboard((state as WebServerState.Running).url) },
                        onShare = { viewModel.shareUrl((state as WebServerState.Running).url) },
                        onOpen = { viewModel.openLocally((state as WebServerState.Running).url) }
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    // Interactive Mechanic Web Access Card
                    MechanicAccessCard(
                        mechanicName = mechanicName,
                        generatedPassword = generatedPassword,
                        onSaveAndGenerate = viewModel::saveMechanicNameAndGenerateOtp,
                        onGenerateNew = viewModel::generateNewOtp,
                        onCopyPassword = viewModel::copyPasswordToClipboard
                    )
                }
            }

            if (state is WebServerState.Error) {
                Text(
                    text = (state as WebServerState.Error).message,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun ServerControlCard(
    isRunning: Boolean,
    onToggle: () -> Unit
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
                        color = if (isRunning) AccentBlue else TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (isRunning) "Servidor web disponible en red local" else "Toca para transmitir por red local",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isRunning,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                )
            }
        }
    }
}

@Composable
private fun UrlDisplayCard(url: String, onCopy: () -> Unit, onShare: () -> Unit, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgPanel),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerColor)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CellTower, null, tint = AccentBlue)
                Spacer(Modifier.width(8.dp))
                Text("Red Local", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Text("Alcance físico estimado: ~50 metros del vehículo", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            
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
            }
        }
    }
}

// Removed ConnectedClientsCard

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MechanicAccessCard(
    mechanicName: String,
    generatedPassword: String?,
    onSaveAndGenerate: (String) -> Unit,
    onGenerateNew: () -> Unit,
    onCopyPassword: (String) -> Unit
) {
    var inputName by remember { mutableStateOf(mechanicName) }
    var isEditing by remember { mutableStateOf(mechanicName.isEmpty()) }
    
    // Update inputName when mechanicName changes from DataStore
    LaunchedEffect(mechanicName) {
        if (!isEditing) inputName = mechanicName
        if (mechanicName.isEmpty()) isEditing = true
    }

    val isValidName = inputName.isNotBlank() && inputName.length <= 10 && inputName.all { it.isLetter() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgPanel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.VpnKey, null, tint = AccentPurple, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("Configuración de Acceso Web", color = TextPrimary, fontWeight = FontWeight.Bold)
            
            Spacer(Modifier.height(16.dp))
            
            if (isEditing) {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { if (it.length <= 10) inputName = it },
                    label = { Text("Nombre del Mecánico") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BgDark,
                        unfocusedContainerColor = BgDark,
                        unfocusedBorderColor = DividerColor,
                        focusedBorderColor = AccentBlue,
                        focusedLabelColor = AccentBlue,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = inputName.isNotBlank() && !isValidName,
                    supportingText = {
                        if (inputName.isNotBlank() && !isValidName) {
                            Text("Solo letras, máximo 10 caracteres", color = AccentRed)
                        } else {
                            Text("Obligatorio para generar la contraseña", color = TextMuted)
                        }
                    }
                )
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = { 
                        onSaveAndGenerate(inputName) 
                        isEditing = false
                    },
                    enabled = isValidName,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar y Generar Contraseña")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Mecánico Activo:", color = TextMuted, fontSize = 12.sp)
                        Text(mechanicName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    TextButton(onClick = { isEditing = true }) {
                        Text("Cambiar nombre", color = AccentBlue)
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                if (generatedPassword != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgDark, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Contraseña OTP actual:", color = TextMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(generatedPassword, color = AccentPurple, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onCopyPassword(generatedPassword) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copiar")
                        }
                        
                        Button(
                            onClick = { onGenerateNew() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Renovar")
                        }
                    }
                } else {
                    Button(
                        onClick = { onGenerateNew() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VpnKey, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Generar Contraseña OTP")
                    }
                }
            }
        }
    }
}
