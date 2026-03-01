package com.obelus.presentation.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SensorsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.domain.model.CanFrame
import com.obelus.presentation.viewmodel.SnifferViewModel
import com.obelus.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnifferScreen(
    viewModel: SnifferViewModel = hiltViewModel(),
    isConnected: Boolean = true // This should come from a Global Connection State
) {
    val frames by viewModel.filteredFrames.collectAsState()
    val isFrozen by viewModel.isFrozen.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    var selectedFrameId by remember { mutableStateOf<String?>(null) }
    var showCompatWarning by remember { mutableStateOf(true) }  // Mostrar al abrir

    // Performance: derivedStateOf for the selected frame
    val selectedFrame = remember(selectedFrameId, frames) {
        derivedStateOf {
            frames.find { it.id == selectedFrameId } ?: frames.firstOrNull()
        }
    }.value

    // Diálogo de compatibilidad al abrir la pantalla
    if (showCompatWarning) {
        AlertDialog(
            onDismissRequest = { showCompatWarning = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Compatibilidad de adaptador", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "El Bus Sniffer requiere un adaptador con soporte de modo promiscuo " +
                    "(comando AT MA). Los adaptadores ELM327 genéricos — incluyendo la " +
                    "mayoría de clones chinos — no soportan esta función.\n\n" +
                    "Adaptadores compatibles: OBDLink MX+, OBDLink EX, STN2120.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCompatWarning = false
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.obdlink.com/products/obdlink-mx/"))
                    context.startActivity(intent)
                }) {
                    Text("Ver adaptadores", color = NeonCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompatWarning = false }) {
                    Text("Continuar de todas formas")
                }
            },
            containerColor = Color(0xFF111111),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BUS SNIFFER", style = FuturisticTypography.titleMedium, color = Color.White)
                        Spacer(Modifier.width(12.dp))
                        StatusDot(
                            color = when {
                                !isConnected -> NeonRed
                                isFrozen -> NeonAmber
                                else -> NeonGreen
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.9f),
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleFreeze() },
                containerColor = if (isFrozen) NeonAmber else NeonCyan,
                contentColor = Color.Black,
                modifier = Modifier.padding(bottom = 80.dp) // Offset for filter bar
            ) {
                Icon(
                    imageVector = if (isFrozen) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = "Toggle Freeze"
                )
            }
        },
        bottomBar = {
            SnifferFilterBar(searchQuery, viewModel::onSearchQueryChange)
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isConnected) {
                EmptySnifferState(
                    icon = Icons.Outlined.SensorsOff,
                    title = "ADAPTER DISCONNECTED",
                    desc = "CONNECT OBD-II ADAPTER TO ENTER THE MATRIX"
                )
            } else if (frames.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("WAITING FOR CAN TRAFFIC...", color = NeonCyan, style = FuturisticTypography.labelSmall)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    // LEFT PANEL: IDs (35%)
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.1f)),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(frames, key = { it.id }) { frame ->
                            CanIdItem(
                                frame = frame,
                                isSelected = selectedFrameId == frame.id,
                                onClick = { selectedFrameId = frame.id }
                            )
                        }
                    }

                    // RIGHT PANEL: Payload (65%)
                    Box(
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                            .background(Color(0xFF050505))
                    ) {
                        selectedFrame?.let {
                            FrameDetailView(it)
                        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("SELECT ID", color = Color.DarkGray, style = FuturisticTypography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CanIdItem(frame: CanFrame, isSelected: Boolean, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val isRecentlyChanged = now - frame.lastChanged < 500
    val isIdle = now - frame.timestamp > 5000
    
    val opacity by animateFloatAsState(
        targetValue = if (isIdle) 0.5f else 1f,
        label = "opacity"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isRecentlyChanged -> NeonCyan
            isSelected -> Color.White
            else -> Color.DarkGray.copy(alpha = 0.3f)
        },
        animationSpec = tween(if (isRecentlyChanged) 100 else 500),
        label = "border"
    )

    val scale by animateFloatAsState(
        targetValue = if (isRecentlyChanged) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .scale(scale)
            .clickable { onClick() }
            .alpha(opacity),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "0x${frame.id}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecentlyChanged) NeonCyan else Color.White
                )
                // Frequency badge placeholder
                Text(
                    text = "LIVE",
                    fontSize = 8.sp,
                    color = if (isRecentlyChanged) NeonCyan else Color.DarkGray,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                text = "${now - frame.timestamp}ms ago",
                fontSize = 9.sp,
                color = if (isRecentlyChanged) NeonCyan.copy(alpha = 0.7f) else Color.Gray
            )
        }
    }
}

@Composable
fun FrameDetailView(frame: CanFrame) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ID: 0x${frame.id}",
                style = FuturisticTypography.headlineMedium,
                color = NeonCyan,
                fontSize = 28.sp
            )
            IconButton(onClick = {
                val clipboardManager = context.getSystemService(android.content.ClipboardManager::class.java)
                val bytes = frame.data.joinToString(" ") { "%02X".format(it) }
                val text = "CAN 0x${frame.id}: $bytes"
                clipboardManager?.setPrimaryClip(
                    android.content.ClipData.newPlainText("CAN Frame", text)
                )
                android.widget.Toast.makeText(context, "Frame copiado", android.widget.Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }

        Text(
            text = "PROTOCOL: ISO-TP CAN 11-BIT",
            style = FuturisticTypography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 2x4 Grid for 8 Bytes
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(frame.data.toList()) { index, byte ->
                ByteCell(
                    byte = byte,
                    isChanged = (frame.changedBytesMask and (1 shl index)) != 0
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Metadata
        TechnicalLine("LAST SEEN", SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(frame.timestamp)))
        TechnicalLine("PERIOD", "--- ms")
        TechnicalLine("BITMASK", "0x${frame.changedBytesMask.toString(16).uppercase().padStart(2, '0')}")

        Spacer(Modifier.weight(1f))
        
        Text(
            text = "MATRIX TERMINAL CORE V2.4",
            style = FuturisticTypography.labelSmall,
            color = Color.DarkGray.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TechnicalLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
fun ByteCell(byte: Byte, isChanged: Boolean) {
    val bgColor by animateColorAsState(
        targetValue = if (isChanged) NeonCyan else Color(0xFF111111),
        animationSpec = tween(300),
        label = "bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isChanged) Color.Black else Color.White,
        animationSpec = tween(300),
        label = "text"
    )

    Card(
        modifier = Modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(0.5.dp, if (isChanged) NeonCyan else Color.White.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = String.format("%02X", byte),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
        }
    }
}

@Composable
fun SnifferFilterBar(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            placeholder = { Text("FILTER BY ID (0x...)", color = Color.DarkGray, fontSize = 12.sp) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0A0A0A),
                unfocusedContainerColor = Color.Black,
                focusedIndicatorColor = NeonCyan,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = NeonCyan,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) }
        )
    }
}

@Composable
fun StatusDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(alpha)
            .background(color, CircleShape)
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
    )
}

@Composable
fun EmptySnifferState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color.DarkGray)
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, style = FuturisticTypography.titleMedium)
        Text(desc, color = Color.Gray, style = FuturisticTypography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { /* Navigate to settings or something */ },
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, NeonCyan)
        ) {
            Text("GO TO SETTINGS", color = NeonCyan)
        }
    }
}
