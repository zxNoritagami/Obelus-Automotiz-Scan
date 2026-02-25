package com.obelus.presentation.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.obelus.data.crash.CrashReporter
import java.io.File

private val BgDark        = Color(0xFF0D1117)
private val BgPanel       = Color(0xFF161B22)
private val TextPrimary   = Color(0xFFE6EDF3)
private val TextMuted     = Color(0xFF8B949E)
private val AccentRed     = Color(0xFFF85149)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen(
    crashReporter: CrashReporter,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(crashReporter.getCrashFiles()) }
    var selectedLog by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs de Errores", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = {
                            crashReporter.deleteAllLogs()
                            logs = emptyList()
                        }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Borrar todos", tint = AccentRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(BgDark).padding(padding)) {
            if (logs.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(logs) { file ->
                        CrashCard(
                            file = file,
                            onShare = { shareLogFile(context, file) },
                            onClick = { selectedLog = file }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (selectedLog != null) {
        AlertDialog(
            onDismissRequest = { selectedLog = null },
            title = { Text(selectedLog?.name ?: "Detalle", fontSize = 14.sp) },
            text = {
                val content = try { selectedLog?.readText() ?: "" } catch (e: Exception) { "Error al leer" }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        content,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLog = null }) { Text("Cerrar") }
            },
            containerColor = BgPanel,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = Color(0xFF3FB950).copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text("No hay errores detectados", color = TextMuted)
        Text("El sistema está funcionando limpiamente", color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

@Composable
private fun CrashCard(file: File, onShare: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = BgPanel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Toca para ver stacktrace", color = TextMuted, fontSize = 11.sp)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, "Compartir", tint = Color(0xFF58A6FF))
            }
        }
    }
}

private fun shareLogFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir log de error"))
}
