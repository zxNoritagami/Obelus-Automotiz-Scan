package com.obelus.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.local.model.Endian

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DbcSignalEditorDialog(
    existingSignal: CanSignal? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        canId: String,
        startBit: Int,
        length: Int,
        factor: Float,
        offset: Float,
        unit: String,
        endian: Endian,
        signed: Boolean
    ) -> Unit
) {
    val isEdit = existingSignal != null

    // Fields pre-filled from existing signal if editing
    var name by remember { mutableStateOf(existingSignal?.name ?: "") }
    var canId by remember { mutableStateOf(existingSignal?.canId ?: "0x000") }
    var startBitStr by remember { mutableStateOf(existingSignal?.startBit?.toString() ?: "0") }
    var lengthStr by remember { mutableStateOf(existingSignal?.bitLength?.toString() ?: "8") }
    var factorStr by remember { mutableStateOf(existingSignal?.scale?.toString() ?: "1.0") }
    var offsetStr by remember { mutableStateOf(existingSignal?.offset?.toString() ?: "0.0") }
    var unit by remember { mutableStateOf(existingSignal?.unit ?: "") }
    var selectedEndian by remember { mutableStateOf(existingSignal?.endianness ?: Endian.LITTLE) }
    var endianExpanded by remember { mutableStateOf(false) }
    var signed by remember { mutableStateOf(existingSignal?.signed ?: false) }

    // Validation errors
    var nameError by remember { mutableStateOf(false) }
    var canIdError by remember { mutableStateOf(false) }
    var bitRangeError by remember { mutableStateOf(false) }
    var lengthError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Editar Señal CAN" else "Nueva Señal CAN") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Nombre *") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("Requerido") } } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                // CAN ID
                OutlinedTextField(
                    value = canId,
                    onValueChange = { canId = it; canIdError = false },
                    label = { Text("CAN ID (hex)") },
                    isError = canIdError,
                    supportingText = if (canIdError) { { Text("Formato: 0x000 o 7E0") } } else null,
                    singleLine = true,
                    placeholder = { Text("0x7E0") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                // StartBit + Length in a row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startBitStr,
                        onValueChange = { startBitStr = it; bitRangeError = false },
                        label = { Text("Start Bit") },
                        isError = bitRangeError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lengthStr,
                        onValueChange = { lengthStr = it; lengthError = false; bitRangeError = false },
                        label = { Text("Longitud (bits)") },
                        isError = lengthError || bitRangeError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (bitRangeError) {
                    Text(
                        "startBit + length debe ser ≤ 64",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Factor + Offset
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = factorStr,
                        onValueChange = { factorStr = it },
                        label = { Text("Factor") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = offsetStr,
                        onValueChange = { offsetStr = it },
                        label = { Text("Offset") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Unit
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unidad (ej: km/h, °C)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )

                // Byte order
                ExposedDropdownMenuBox(
                    expanded = endianExpanded,
                    onExpandedChange = { endianExpanded = !endianExpanded }
                ) {
                    OutlinedTextField(
                        value = when (selectedEndian) {
                            Endian.LITTLE -> "LITTLE_ENDIAN"
                            Endian.BIG -> "BIG_ENDIAN"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Byte Order") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endianExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = endianExpanded,
                        onDismissRequest = { endianExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("LITTLE_ENDIAN") },
                            onClick = { selectedEndian = Endian.LITTLE; endianExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("BIG_ENDIAN") },
                            onClick = { selectedEndian = Endian.BIG; endianExpanded = false }
                        )
                    }
                }

                // Signed switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Con signo (signed)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = signed, onCheckedChange = { signed = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Validate
                var valid = true
                if (name.trim().isEmpty()) { nameError = true; valid = false }

                val sb = startBitStr.toIntOrNull()
                val len = lengthStr.toIntOrNull()

                if (sb == null || sb < 0 || sb > 63) { bitRangeError = true; valid = false }
                if (len == null || len < 1 || len > 64) { lengthError = true; valid = false }
                if (sb != null && len != null && (sb + len) > 64) { bitRangeError = true; valid = false }

                if (!valid) return@TextButton

                onSave(
                    name.trim(),
                    canId.trim().uppercase(),
                    sb!!,
                    len!!,
                    factorStr.toFloatOrNull() ?: 1f,
                    offsetStr.toFloatOrNull() ?: 0f,
                    unit.trim(),
                    selectedEndian,
                    signed
                )
                onDismiss()
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
