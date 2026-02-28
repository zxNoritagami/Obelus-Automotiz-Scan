package com.obelus.presentation.ui.components

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// DeviceItem.kt
// Componente reutilizable para mostrar un adaptador Bluetooth en una lista.
// ─────────────────────────────────────────────────────────────────────────────

/** Modelo UI independiente de [BluetoothDevice] para facilitar Previews y tests. */
data class DeviceUiModel(
    val name: String,
    val address: String,
    val isPaired: Boolean,
    val signalStrength: Int? = null   // RSSI en dBm, null si no disponible
)

/** Estado de conexión del ítem. */
enum class DeviceConnectionState { IDLE, CONNECTING, CONNECTED }

/**
 * Tarjeta de dispositivo Bluetooth para [LazyColumn].
 *
 * @param device    Datos del dispositivo a mostrar.
 * @param state     Estado de conexión actual para este ítem.
 * @param onClick   Callback al pulsar "Conectar" o el ítem completo.
 */
@Composable
fun DeviceItem(
    device: DeviceUiModel,
    state: DeviceConnectionState = DeviceConnectionState.IDLE,
    onClick: () -> Unit = {}
) {
    val isConnected  = state == DeviceConnectionState.CONNECTED
    val isConnecting = state == DeviceConnectionState.CONNECTING

    val borderColor by animateColorAsState(
        targetValue = when {
            isConnected  -> Color(0xFF00C853)  // verde
            isConnecting -> Color(0xFFFFB300)  // ámbar
            else         -> Color.Transparent
        },
        animationSpec = tween(300),
        label = "DeviceItemBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isConnected || isConnecting) 1.5.dp else 0.dp,
                color  = borderColor,
                shape  = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = state == DeviceConnectionState.IDLE, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Icono BT ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isConnected  -> Color(0xFF00C853)
                            isConnecting -> Color(0xFFFFB300)
                            else         -> MaterialTheme.colorScheme.primary
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isConnected  -> Icons.Default.BluetoothConnected
                        isConnecting -> Icons.Default.BluetoothSearching
                        else         -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // ── Nombre + MAC ──────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (device.isPaired) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Emparejado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                device.signalStrength?.let { rssi ->
                    Text(
                        text = "Señal: $rssi dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = rssiColor(rssi),
                        fontSize = 11.sp
                    )
                }
            }

            // ── Botón acción ──────────────────────────────────────────────────
            when (state) {
                DeviceConnectionState.IDLE       -> OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Conectar", style = MaterialTheme.typography.labelMedium) }

                DeviceConnectionState.CONNECTING -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("...", style = MaterialTheme.typography.labelSmall)
                }

                DeviceConnectionState.CONNECTED  -> Surface(
                    color = Color(0xFF00C853).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        "✓ Activo",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF00C853),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun rssiColor(rssi: Int) = when {
    rssi >= -60  -> Color(0xFF00C853)   // buena
    rssi >= -75  -> Color(0xFFFFB300)   // media
    else         -> Color(0xFFE53935)   // débil
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "DeviceItem – Idle")
@Composable
private fun PreviewDeviceItemIdle() {
    MaterialTheme {
        DeviceItem(
            device = DeviceUiModel("ELM327 v2.1", "00:1D:A5:68:98:8B", isPaired = true, signalStrength = -62),
            state  = DeviceConnectionState.IDLE
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "DeviceItem – Connecting")
@Composable
private fun PreviewDeviceItemConnecting() {
    MaterialTheme {
        DeviceItem(
            device = DeviceUiModel("OBD2 BT Adapter", "00:1B:DC:F2:03:D0", isPaired = false),
            state  = DeviceConnectionState.CONNECTING
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "DeviceItem – Connected")
@Composable
private fun PreviewDeviceItemConnected() {
    MaterialTheme {
        DeviceItem(
            device = DeviceUiModel("ELM327 v2.1", "00:1D:A5:68:98:8B", isPaired = true, signalStrength = -55),
            state  = DeviceConnectionState.CONNECTED
        )
    }
}
