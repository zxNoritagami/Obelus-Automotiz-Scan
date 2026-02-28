package com.obelus.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.bluetooth.ConnectionState
import com.obelus.protocol.OBD2Protocol

// ─────────────────────────────────────────────────────────────────────────────
// ScanHeader.kt
// Barra superior de la pantalla de escaneo: estado BT, protocolo, acciones.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Encabezado de la sesión de diagnóstico.
 *
 * @param connectionState   Estado de conexión Bluetooth actual.
 * @param detectedProtocol  Protocolo OBD2 detectado (null = desconocido).
 * @param dbcName           Nombre del archivo DBC cargado (null = "Genérico").
 * @param onDisconnect      Callback al pulsar el botón de desconexión.
 * @param onLoadDbc         Callback al pulsar "Cargar DBC".
 */
@Composable
fun ScanHeader(
    connectionState: ConnectionState  = ConnectionState.Disconnected,
    detectedProtocol: OBD2Protocol?   = null,
    dbcName: String?                  = null,
    onDisconnect: () -> Unit          = {},
    onLoadDbc: () -> Unit             = {}
) {
    val isConnected = connectionState is ConnectionState.Connected
    val isReconnecting = connectionState is ConnectionState.Reconnecting

    val ledColor by animateColorAsState(
        targetValue = when {
            isConnected    -> Color(0xFF00C853)
            isReconnecting -> Color(0xFFFFB300)
            connectionState is ConnectionState.Error -> Color(0xFFE53935)
            else           -> Color.Gray
        },
        animationSpec = tween(400),
        label = "LedColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── LED de estado ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(ledColor)
        )

        // ── Nombre del dispositivo ────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (connectionState) {
                    is ConnectionState.Connected   -> connectionState.deviceName
                    is ConnectionState.Connecting  -> "Conectando…"
                    is ConnectionState.Reconnecting-> "Reconectando (${connectionState.attempt}/${connectionState.maxAttempts})…"
                    is ConnectionState.Error       -> "Error: ${connectionState.message}"
                    else                           -> "Sin conexión"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Badge de protocolo
                ProtocolBadge(protocol = detectedProtocol)
                // Badge DBC
                dbcName?.let { DbcBadge(name = it) }
            }
        }

        // ── Botón cargar DBC ──────────────────────────────────────────────────
        IconButton(onClick = onLoadDbc) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = "Cargar DBC",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // ── Botón desconectar ─────────────────────────────────────────────────
        IconButton(onClick = onDisconnect, enabled = isConnected || isReconnecting) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Desconectar",
                tint = if (isConnected) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Badges ────────────────────────────────────────────────────────────────────

@Composable
fun ProtocolBadge(protocol: OBD2Protocol?) {
    val label = protocol?.let {
        when {
            it.protocolName.contains("CAN", ignoreCase = true) ->
                "CAN ${if (it.atCommand.contains("6") || it.atCommand.contains("7")) "500K" else "250K"}"
            it.protocolName.contains("J1850") -> "J1850"
            it.protocolName.contains("ISO 9141") -> "K-Line"
            it.protocolName.contains("ISO 14230") -> "KWP2000"
            else -> it.protocolName.take(12)
        }
    } ?: "Auto"

    val bgColor = if (protocol != null) Color(0xFF1565C0).copy(alpha = 0.2f)
                  else Color.Gray.copy(alpha = 0.15f)
    val textColor = if (protocol != null) Color(0xFF42A5F5) else Color.Gray

    Surface(color = bgColor, shape = RoundedCornerShape(50)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun DbcBadge(name: String) {
    Surface(
        color = Color(0xFF1B5E20).copy(alpha = 0.25f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = Color(0xFF66BB6A),
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = name.take(14),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF66BB6A),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ScanHeader – Connected")
@Composable
private fun PreviewHeaderConnected() {
    MaterialTheme {
        ScanHeader(
            connectionState  = ConnectionState.Connected("ELM327 v2.1", "00:1D:A5"),
            detectedProtocol = OBD2Protocol.ISO_15765_4_CAN_11BIT_500K,
            dbcName          = "VW_Golf_MK7.dbc"
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ScanHeader – Disconnected")
@Composable
private fun PreviewHeaderDisconnected() {
    MaterialTheme { ScanHeader() }
}
