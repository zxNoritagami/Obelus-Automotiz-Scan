package com.obelus.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.data.crash.CrashReporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogsScreen(
    crashReporter: CrashReporter,
    onBack: () -> Unit
) {
    var crashFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        crashFiles = crashReporter.getCrashFiles()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFFF5555)) },
            title = { Text("Eliminar todos los logs", fontWeight = FontWeight.Bold) },
            text = { Text("Se eliminarán ${crashFiles.size} archivos de crash. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        crashReporter.deleteAllLogs()
                        crashFiles = emptyList()
                        selectedFile = null
                        fileContent = ""
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5555))
                ) { Text("Eliminar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "CRASH LOGS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (crashFiles.isNotEmpty()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = "Eliminar todos",
                                tint = Color(0xFFFF5555)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (crashFiles.isEmpty()) {
            // Estado vacío
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00FF88),
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        "Sin crashes registrados",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF88)
                    )
                    Text(
                        "El sistema está operando correctamente.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF5555).copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = Color(0xFFFF5555),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${crashFiles.size} crash(es) encontrado(s)",
                            color = Color(0xFFFF5555),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(crashFiles) { file ->
                    val isExpanded = selectedFile == file
                    CrashLogItem(
                        file = file,
                        isExpanded = isExpanded,
                        content = if (isExpanded) fileContent else "",
                        onClick = {
                            if (isExpanded) {
                                selectedFile = null
                                fileContent = ""
                            } else {
                                selectedFile = file
                                fileContent = try {
                                    file.readText()
                                } catch (e: Exception) {
                                    "Error al leer el archivo: ${e.message}"
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CrashLogItem(
    file: File,
    isExpanded: Boolean,
    content: String,
    onClick: () -> Unit
) {
    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    }
    val formattedDate = remember(file) {
        dateFormatter.format(Date(file.lastModified()))
    }
    // Extraer componente del nombre del archivo (crash_TIMESTAMP_COMPONENT.txt)
    val component = remember(file) {
        file.nameWithoutExtension.split("_").drop(2).joinToString("_").ifEmpty { "unknown" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded)
                Color(0xFFFF5555).copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isExpanded) 1.dp else 0.dp,
            color = if (isExpanded) Color(0xFFFF5555).copy(alpha = 0.5f) else Color.Transparent
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.BugReport,
                        null,
                        tint = Color(0xFFFF5555),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            component,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Text(
                            formattedDate,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = Color.Gray
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFFF5555).copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = content,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFE0E0E0),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
