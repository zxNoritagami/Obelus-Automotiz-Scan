package com.obelus.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.local.entity.DbcDefinition
import com.obelus.data.local.model.Endian
import com.obelus.presentation.ui.components.CreateDbcDialog
import com.obelus.presentation.ui.components.DbcSignalEditorDialog
import com.obelus.presentation.viewmodel.DbcEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DbcEditorScreen(
    onBack: () -> Unit,
    viewModel: DbcEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showCreateDefinitionDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva definición DBC")
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Left panel: definition list (30%) ─────────────────────────────
            DefinitionListPanel(
                definitions = uiState.definitions,
                selectedDefinition = uiState.selectedDefinition,
                isLoading = uiState.isLoading,
                onSelect = { viewModel.selectDefinition(it) },
                onDelete = { viewModel.deleteDefinition(it) },
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxHeight()
            )

            VerticalDivider()

            // ── Right panel: definition detail (70%) ──────────────────────────
            DefinitionDetailPanel(
                definition = uiState.selectedDefinition,
                signals = uiState.selectedSignals,
                isLoading = uiState.isLoading,
                onUpdateDefinition = { name, desc, proto ->
                    viewModel.updateDefinition(name, desc, proto)
                },
                onAddSignal = { viewModel.showSignalEditorDialog() },
                onEditSignal = { viewModel.showSignalEditorDialog(it) },
                onDeleteSignal = { viewModel.deleteSignal(it) },
                modifier = Modifier
                    .weight(0.70f)
                    .fillMaxHeight()
            )
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (uiState.showCreateDialog) {
        CreateDbcDialog(
            onDismiss = { viewModel.dismissCreateDialog() },
            onCreate = { name, desc, proto ->
                viewModel.createDefinition(name, desc, proto)
            }
        )
    }

    if (uiState.showSignalDialog) {
        DbcSignalEditorDialog(
            existingSignal = uiState.editingSignal,
            onDismiss = { viewModel.dismissSignalDialog() },
            onSave = { name, canId, startBit, length, factor, offset, unit, endian, signed ->
                viewModel.createCustomSignal(name, canId, startBit, length, factor, offset, unit, endian, signed)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Left panel — list of DbcDefinitions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DefinitionListPanel(
    definitions: List<DbcDefinition>,
    selectedDefinition: DbcDefinition?,
    isLoading: Boolean,
    onSelect: (DbcDefinition) -> Unit,
    onDelete: (DbcDefinition) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Definiciones DBC",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
        HorizontalDivider()

        if (isLoading && definitions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (definitions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sin definiciones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Usa el botón + para crear una",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        LazyColumn {
            items(definitions, key = { it.id }) { def ->
                DbcDefinitionCard(
                    definition = def,
                    isSelected = selectedDefinition?.id == def.id,
                    onClick = { onSelect(def) },
                    onDelete = { onDelete(def) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DbcDefinitionCard(
    definition: DbcDefinition,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val background = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        definition.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    // Built-in / Custom badge
                    if (definition.isBuiltIn) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Built-in", style = MaterialTheme.typography.labelSmall) }
                        )
                    } else {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Custom", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                if (!definition.description.isNullOrBlank()) {
                    Text(
                        definition.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${definition.signalCount} señal(es) · ${definition.protocol}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Context menu (delete only — not for built-ins)
            if (!definition.isBuiltIn) {
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

// ─────────────────────────────────────────────────────────────────────────────
// Right panel — definition detail + signals
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DefinitionDetailPanel(
    definition: DbcDefinition?,
    signals: List<CanSignal>,
    isLoading: Boolean,
    onUpdateDefinition: (name: String, desc: String?, proto: String) -> Unit,
    onAddSignal: () -> Unit,
    onEditSignal: (CanSignal) -> Unit,
    onDeleteSignal: (CanSignal) -> Unit,
    modifier: Modifier = Modifier
) {
    if (definition == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Selecciona una definición DBC",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "o crea una nueva con el botón +",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(modifier = modifier.padding(16.dp)) {
        // ── Header ─────────────────────────────────────────────────────────
        Text(
            definition.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Protocolo: ${definition.protocol}  ·  ${if (definition.isBuiltIn) "Integrado (solo lectura)" else "Personalizado"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // ── Signals section ────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Señales (${signals.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (!definition.isBuiltIn) {
                FilledTonalButton(
                    onClick = onAddSignal,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Agregar Señal", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        } else if (signals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Sin señales asociadas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(signals, key = { it.id }) { signal ->
                    DbcSignalItem(
                        signal = signal,
                        readOnly = definition.isBuiltIn,
                        onEdit = { onEditSignal(signal) },
                        onDelete = { onDeleteSignal(signal) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Signal row item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DbcSignalItem(
    signal: CanSignal,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        signal.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (signal.isCustom) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "CUSTOM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    buildString {
                        append("CAN ID: ${signal.canId}")
                        append("  |  bit${signal.startBit}:${signal.bitLength}")
                        append("  |  ×${signal.scale}+${signal.offset}")
                        if (!signal.unit.isNullOrBlank()) append("  ${signal.unit}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    buildString {
                        append(if (signal.endianness == Endian.LITTLE) "Little-endian" else "Big-endian")
                        append("  ·  ${if (signal.signed) "Signed" else "Unsigned"}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (!readOnly) {
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opciones señal",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
