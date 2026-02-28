package com.obelus.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.obelus.ui.theme.FuturisticTypography
import com.obelus.ui.theme.NeonCyan
import com.obelus.ui.theme.NeonGreen
import com.obelus.ui.theme.NeonRed

@Composable
fun StatusBar(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "DASHBOARD",
            style = FuturisticTypography.titleMedium,
            color = NeonCyan
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            val statusColor = if (isConnected) NeonGreen else NeonRed
            val iconShape = if(isConnected) Icons.Default.Bluetooth else Icons.Default.Warning
            
            Text(
                text = if (isConnected) "OBD ONLINE" else "OFFLINE",
                style = FuturisticTypography.labelMedium,
                color = statusColor
            )

            // Indicador de conexión "Neón"
            Box(contentAlignment = Alignment.Center) {
                if (isConnected) {
                    com.obelus.ui.components.animations.PulseRing(modifier = Modifier.size(36.dp), color = NeonGreen)
                }

                Icon(
                    imageVector = iconShape,
                    contentDescription = "Status",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .padding(4.dp)
                )
            }
        }
    }
}
