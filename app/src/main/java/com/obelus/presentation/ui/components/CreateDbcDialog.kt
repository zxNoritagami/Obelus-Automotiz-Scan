package com.obelus.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private val DBC_PROTOCOLS = listOf("CAN", "CANFD", "OBD2", "UDS")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDbcDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?, protocol: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedProtocol by remember { mutableStateOf(DBC_PROTOCOLS[0]) }
    var protocolExpanded by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Definición DBC") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text("Nombre *") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("El nombre es obligatorio") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                // Protocol dropdown
                ExposedDropdownMenuBox(
                    expanded = protocolExpanded,
                    onExpandedChange = { protocolExpanded = !protocolExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedProtocol,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Protocolo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = protocolExpanded,
                        onDismissRequest = { protocolExpanded = false }
                    ) {
                        DBC_PROTOCOLS.forEach { protocol ->
                            DropdownMenuItem(
                                text = { Text(protocol) },
                                onClick = {
                                    selectedProtocol = protocol
                                    protocolExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().isEmpty()) {
                    nameError = true
                    return@TextButton
                }
                onCreate(name.trim(), description.trim().takeIf { it.isNotEmpty() }, selectedProtocol)
                onDismiss()
            }) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
