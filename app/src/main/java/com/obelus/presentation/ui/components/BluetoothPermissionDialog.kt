package com.obelus.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// BluetoothPermissionDialog.kt
// Diálogo explicativo de permisos Bluetooth para Android 12+ y legacy.
// ─────────────────────────────────────────────────────────────────────────────

/** Estado de la solicitud de permisos. */
enum class PermissionState {
    /** Primera vez o usuario no ha respondido. */
    SHOULD_REQUEST,
    /** Usuario negó pero puede volver a preguntar. */
    DENIED,
    /** Usuario negó permanentemente → redirigir a Settings. */
    PERMANENTLY_DENIED
}

/**
 * Diálogo que explica por qué se necesitan permisos Bluetooth.
 *
 * @param permissionState Estado actual de los permisos.
 * @param onRequest       El usuario acepta conceder los permisos.
 * @param onOpenSettings  El usuario acepta ir a Configuración del sistema.
 * @param onDismiss       El usuario cierra el diálogo (si se permite).
 */
@Composable
fun BluetoothPermissionDialog(
    permissionState: PermissionState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = when (permissionState) {
                    PermissionState.PERMANENTLY_DENIED -> "Permisos Bloqueados"
                    PermissionState.DENIED             -> "Permisos Necesarios"
                    else                               -> "Acceso Bluetooth"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (permissionState) {
                        PermissionState.PERMANENTLY_DENIED ->
                            "Has denegado los permisos permanentemente.\n\n" +
                            "Ve a Configuración → Permisos de la app → Bluetooth " +
                            "y habilítalos manualmente para continuar."

                        PermissionState.DENIED ->
                            "La aplicación necesita permiso de Bluetooth para " +
                            "descubrir y conectar adaptadores OBD2 ELM327.\n\n" +
                            "Sin este permiso no es posible realizar diagnósticos."

                        else ->
                            "Para escanear adaptadores ELM327 cercanos necesitamos " +
                            "acceder al Bluetooth de tu dispositivo.\n\n" +
                            "No se comparten datos con terceros."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (permissionState == PermissionState.PERMANENTLY_DENIED) {
                    // Hint visual de dónde ir en Settings
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Ajustes → Apps → Obelus → Permisos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = if (permissionState == PermissionState.PERMANENTLY_DENIED) onOpenSettings else onRequest,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (permissionState == PermissionState.PERMANENTLY_DENIED)
                        "Abrir Configuración" else "Conceder Permisos"
                )
            }
        },
        dismissButton = {
            if (permissionState != PermissionState.PERMANENTLY_DENIED) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Ahora no",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "PermissionDialog – First request")
@Composable
private fun PreviewDialogRequest() {
    MaterialTheme {
        BluetoothPermissionDialog(
            permissionState = PermissionState.SHOULD_REQUEST,
            onRequest = {}, onOpenSettings = {}
        )
    }
}

@Preview(name = "PermissionDialog – Denied")
@Composable
private fun PreviewDialogDenied() {
    MaterialTheme {
        BluetoothPermissionDialog(
            permissionState = PermissionState.DENIED,
            onRequest = {}, onOpenSettings = {}
        )
    }
}

@Preview(name = "PermissionDialog – Permanently denied")
@Composable
private fun PreviewDialogPermanentlyDenied() {
    MaterialTheme {
        BluetoothPermissionDialog(
            permissionState = PermissionState.PERMANENTLY_DENIED,
            onRequest = {}, onOpenSettings = {}
        )
    }
}
