package com.obelus.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.local.entity.DatabaseFile
import com.obelus.data.local.model.FileType

// ─────────────────────────────────────────────────────────────────────────────
// ScanDialogs.kt
// Tres diálogos de la pantalla de escaneo:
//   1. SelectDatabaseDialog – elegir DBC importado
//   2. AddSignalDialog      – buscar y agregar señales
//   3. ExportDialog         – opciones de exportación
// ─────────────────────────────────────────────────────────────────────────────

// ══════════════════════════════════════════════════════════════════════════════
// 1. SelectDatabaseDialog
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Lista de archivos DBC importados para seleccionar el activo.
 *
 * @param databases     Lista de [DatabaseFile] disponibles.
 * @param selectedFile  Archivo actualmente activo.
 * @param onSelect      Callback con el [DatabaseFile] elegido.
 * @param onDismiss     Cierra el diálogo sin cambiar.
 */
@Composable
fun SelectDatabaseDialog(
    databases: List<DatabaseFile> = emptyList(),
    selectedFile: DatabaseFile?   = null,
    onSelect: (DatabaseFile) -> Unit = {},
    onDismiss: () -> Unit         = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Seleccionar base de datos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (databases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No hay archivos importados.\nUsa \'Cargar DBC\' para importar uno.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(databases) { db ->
                        val isSelected = db.fileName == selectedFile?.fileName
                        TextButton(
                            onClick  = { onSelect(db); onDismiss() },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        db.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    val detail = buildString {
                                        db.vehicleMake?.let { append("$it ") }
                                        db.vehicleModel?.let { append(it) }
                                        db.vehicleYear?.let { append(" $it") }
                                        if (isNotEmpty()) append(" • ")
                                        append("${db.signalCount} señales")
                                    }
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// 2. AddSignalDialog
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Diálogo para buscar y agregar señales al escaneo personalizado.
 *
 * @param availableSignals  Señales disponibles (del DBC o genéricas).
 * @param selectedSignals   IDs de señales ya seleccionadas.
 * @param onAdd             Callback al confirmar la selección.
 * @param onDismiss         Cierra el diálogo.
 */
@Composable
fun AddSignalDialog(
    availableSignals: List<CanSignal>   = emptyList(),
    selectedSignals: Set<Long>          = emptySet(),
    onAdd: (Set<Long>) -> Unit          = {},
    onDismiss: () -> Unit               = {}
) {
    var query by remember { mutableStateOf("") }
    val pending = remember { mutableStateSetOf<Long>().apply { addAll(selectedSignals) } }

    val filtered = remember(query, availableSignals) {
        if (query.isBlank()) availableSignals
        else availableSignals.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.canId.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("Agregar señales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar señal o PID…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    "${pending.size} señal(es) seleccionada(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(filtered, key = { it.id }) { sig ->
                        val checked = sig.id in pending
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    if (on) pending.add(sig.id) else pending.remove(sig.id)
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(sig.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "PID: ${sig.canId} • ${sig.unit ?: "—"} • ${sig.bitLength}bit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(pending.toSet()); onDismiss() },
                shape = RoundedCornerShape(10.dp)
            ) { Text("Agregar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// 3. ExportDialog
// ══════════════════════════════════════════════════════════════════════════════

enum class ExportFormat { PDF, CSV, SHARE }

/**
 * Diálogo de opciones de exportación de la sesión.
 */
@Composable
fun ExportDialog(
    sessionId: Long?             = null,
    readingCount: Int            = 0,
    onExport: (ExportFormat) -> Unit = {},
    onDismiss: () -> Unit        = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        },
        title = {
            Text("Exportar sesión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (readingCount == 0) {
                    Text(
                        "No hay lecturas para exportar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "$readingCount lectura(s) disponible(s) para exportar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    ExportOptionButton(
                        icon = Icons.Default.PictureAsPdf,
                        label = "Exportar como PDF",
                        description = "Informe visual con gráficas",
                        enabled = readingCount > 0,
                        onClick = { onExport(ExportFormat.PDF); onDismiss() }
                    )
                    ExportOptionButton(
                        icon = Icons.Default.TableChart,
                        label = "Exportar como CSV",
                        description = "Datos crudos para Excel / análisis",
                        enabled = readingCount > 0,
                        onClick = { onExport(ExportFormat.CSV); onDismiss() }
                    )
                    ExportOptionButton(
                        icon = Icons.Default.Share,
                        label = "Compartir",
                        description = "Enviar por WhatsApp, correo, etc.",
                        enabled = readingCount > 0,
                        onClick = { onExport(ExportFormat.SHARE); onDismiss() }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun ExportOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "SelectDatabaseDialog – empty")
@Composable
private fun PreviewSelectDbEmpty() {
    MaterialTheme { SelectDatabaseDialog() }
}

@Preview(name = "ExportDialog – with readings")
@Composable
private fun PreviewExportDialog() {
    MaterialTheme { ExportDialog(sessionId = 1L, readingCount = 342) }
}
