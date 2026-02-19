package com.obelus.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.protocol.uds.*
import com.obelus.presentation.viewmodel.SecurityAccessViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Paleta de colores
// ─────────────────────────────────────────────────────────────────────────────
private val BgDark       = Color(0xFF0D1117)
private val BgPanel      = Color(0xFF161B22)
private val BgCard       = Color(0xFF1C2128)
private val AccentGreen  = Color(0xFF3FB950)
private val AccentRed    = Color(0xFFEF4444)
private val AccentOrange = Color(0xFFF59E0B)
private val AccentBlue   = Color(0xFF58A6FF)
private val AccentPurple = Color(0xFFA371F7)
private val TextPrimary  = Color(0xFFE6EDF3)
private val TextMuted    = Color(0xFF8B949E)
private val Divider      = Color(0xFF30363D)

private val MANUFACTURERS = listOf("GENERIC", "VAG", "BMW", "TOYOTA")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAccessScreen(
    viewModel: SecurityAccessViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState     by viewModel.uiState.collectAsState()
    var showMfrPicker by remember { mutableStateOf(false) }
    var showHistory   by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Security Access", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("UDS SID 0x27", color = AccentPurple, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                actions = {
                    // Historial
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.Default.History, contentDescription = "Historial", tint = TextPrimary)
                    }
                    // Fabricante
                    TextButton(onClick = { showMfrPicker = true }) {
                        Text(uiState.manufacturer, color = AccentBlue, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPanel)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgDark)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Banner advertencia algoritmos placeholder ──────────────────
            PlaceholderWarningBanner()

            // ── ECU ID ─────────────────────────────────────────────────────
            EcuIdField(
                value = uiState.ecuIdHex,
                onValueChange = viewModel::setEcuId
            )

            // ── Selector de nivel de seguridad ─────────────────────────────
            SectionTitle("Nivel de Seguridad")
            LevelSelector(
                levels   = viewModel.availableLevels,
                selected = uiState.selectedLevel,
                onSelect = viewModel::selectLevel
            )

            // ── Panel de Seed ──────────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            SectionTitle("Paso 1 — Request Seed")
            SeedPanel(
                uiState      = uiState,
                onRequestSeed = viewModel::requestSeed,
                onAutoAccess  = viewModel::performAutoAccess
            )

            // ── Panel de Key ───────────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            SectionTitle("Paso 2 — Send Key")
            KeyPanel(
                uiState        = uiState,
                onKeyChange    = viewModel::setManualKey,
                onSendKey      = viewModel::sendKeyManual
            )

            // ── Estado actual ──────────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            AccessStatePanel(state = uiState.accessState)

            // ── Historial ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showHistory && uiState.attemptHistory.isNotEmpty(),
                enter   = fadeIn(tween(300)),
                exit    = fadeOut(tween(300))
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    SectionTitle("Historial de intentos")
                    AttemptHistory(attempts = uiState.attemptHistory)
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // Diálogo selector de fabricante
    if (showMfrPicker) {
        ManufacturerDialog(
            current   = uiState.manufacturer,
            onSelect  = { mfr -> viewModel.setManufacturer(mfr); showMfrPicker = false },
            onDismiss = { showMfrPicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENTES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaceholderWarningBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = AccentOrange.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null,
                tint = AccentOrange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Algoritmos en modo PLACEHOLDER",
                    color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
                Text(
                    "Los algoritmos de cálculo de Key requieren ingeniería inversa por ECU. " +
                    "Usa entrada manual hasta disponer del algoritmo real.",
                    color = AccentOrange.copy(alpha = 0.8f), fontSize = 11.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcuIdField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ECU ID:", color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(55.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            prefix = { Text("0x", color = TextMuted, fontFamily = FontFamily.Monospace) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor   = AccentBlue,
                unfocusedTextColor = AccentBlue,
                focusedBorderColor = AccentPurple,
                unfocusedBorderColor = Divider
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.width(130.dp)
        )
        Text(
            "ECM=7E0 • TCM=7E1\nABS=7A0 • SRS=7B0",
            color = TextMuted, fontSize = 9.sp, lineHeight = 13.sp
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        color = TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 12.dp))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LevelSelector(
    levels: List<SecurityAccessLevel>,
    selected: SecurityAccessLevel?,
    onSelect: (SecurityAccessLevel) -> Unit
) {
    levels.forEach { level ->
        val isSelected = level.level == selected?.level
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clickable { onSelect(level) },
            color  = if (isSelected) AccentPurple.copy(alpha = 0.15f) else BgCard,
            shape  = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = if (isSelected) 1.dp else 0.5.dp,
                color = if (isSelected) AccentPurple else Divider
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            level.levelHex,
                            color = if (isSelected) AccentPurple else AccentBlue,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(level.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                    Text(level.description, color = TextMuted, fontSize = 11.sp)
                }
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        tint = AccentPurple, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeedPanel(
    uiState: com.obelus.presentation.viewmodel.SecurityAccessUiState,
    onRequestSeed: () -> Unit,
    onAutoAccess: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        color = BgCard,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Seed display
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Seed:", color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                Text(
                    if (uiState.seedHex.isNotEmpty()) uiState.seedHex else "⎯ Sin seed solicitado ⎯",
                    color = if (uiState.seedHex.isNotEmpty()) AccentGreen else TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRequestSeed,
                    enabled = !uiState.isLoading && uiState.selectedLevel != null,
                    colors  = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isLoading && uiState.accessState is SecurityAccessState.RequestingSeed) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Request Seed", fontSize = 12.sp)
                }
                if (uiState.algorithmImplemented) {
                    OutlinedButton(
                        onClick = onAutoAccess,
                        enabled = !uiState.isLoading && uiState.selectedLevel != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Auto", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyPanel(
    uiState: com.obelus.presentation.viewmodel.SecurityAccessUiState,
    onKeyChange: (String) -> Unit,
    onSendKey: () -> Unit
) {
    val enabled = uiState.seedHex.isNotEmpty() && !uiState.isLoading

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        color = BgCard,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            OutlinedTextField(
                value         = uiState.manualKeyHex,
                onValueChange = onKeyChange,
                label         = { Text("Key calculada (HEX)", color = TextMuted, fontSize = 12.sp) },
                placeholder   = { Text("AA BB CC DD", color = TextMuted.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                singleLine    = true,
                enabled       = enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor    = AccentGreen,
                    unfocusedTextColor  = AccentGreen,
                    focusedBorderColor  = AccentGreen,
                    unfocusedBorderColor = Divider,
                    disabledBorderColor = Divider.copy(alpha = 0.4f),
                    disabledTextColor   = TextMuted
                ),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick  = onSendKey,
                enabled  = enabled && uiState.manualKeyHex.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading && uiState.accessState is SecurityAccessState.SendingKey) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Send Key", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccessStatePanel(state: SecurityAccessState) {
    val (icon, color, title, subtitle) = when (state) {
        SecurityAccessState.Idle ->
            Tuple4(Icons.Default.Shield, TextMuted, "En espera", "Selecciona nivel y solicita el seed")
        SecurityAccessState.RequestingSeed ->
            Tuple4(Icons.Default.Sync, AccentPurple, "Solicitando Seed...", "Esperando respuesta de la ECU")
        is SecurityAccessState.SeedReceived ->
            Tuple4(Icons.Default.CheckCircle, AccentBlue, "Seed recibido", "Ingresa o calcula la Key para continuar")
        SecurityAccessState.SendingKey ->
            Tuple4(Icons.Default.Sync, AccentOrange, "Enviando Key...", "Esperando verificación de la ECU")
        is SecurityAccessState.AccessGranted ->
            Tuple4(Icons.Default.LockOpen, AccentGreen, "✅ ACCESO CONCEDIDO", "Nivel 0x%02X activo — ECU desbloqueada".format(state.level))
        is SecurityAccessState.NegativeResponse ->
            Tuple4(Icons.Default.Lock, AccentRed,
                "❌ NRC 0x%02X: %s".format(state.rawCode, state.nrc.labelEs),
                state.nrc.advice)
        is SecurityAccessState.Error ->
            Tuple4(Icons.Default.Warning, AccentOrange, "Error", state.message)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(subtitle, color = color.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AttemptHistory(attempts: List<SecurityAccessAttempt>) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        attempts.take(10).forEach { attempt ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                color = BgCard,
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Nivel 0x%02X".format(attempt.level),
                            color = AccentPurple, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold)
                        Text(attempt.result, color = TextMuted, fontSize = 10.sp)
                    }
                    Text("Seed: ${attempt.seedHex}", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text("Key:  ${attempt.keyHex}", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ManufacturerDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPanel,
        title = { Text("Fabricante / Algoritmo", color = TextPrimary) },
        text = {
            Column {
                Text("Selecciona el fabricante para elegir el algoritmo de Key:",
                    color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                MANUFACTURERS.forEach { mfr ->
                    val label = when (mfr) {
                        "VAG"    -> "VAG — VW / Audi / Seat / Skoda"
                        "BMW"    -> "BMW / Mini"
                        "TOYOTA" -> "Toyota / Lexus"
                        else     -> "Genérico (entrada manual)"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mfr) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mfr == current, onClick = { onSelect(mfr) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(label, color = TextPrimary, fontSize = 13.sp)
                            Text("(Algoritmo: PLACEHOLDER — necesita RE)",
                                color = AccentOrange, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMuted) } }
    )
}

// Helper para destructuring de 4 elementos
private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component4() = d
