package com.obelus.ui.components.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.ui.theme.NeonCyan

@Composable
fun LogTimeline(
    isPlaying: Boolean,
    progress: Float, // 0f to 1f
    totalTimeMs: Long,
    onPlayPause: () -> Unit,
    onScrub: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTimeString = formatTime((totalTimeMs * progress).toLong())
    val totalTimeString = formatTime(totalTimeMs)

    Card(
        modifier = modifier.fillMaxWidth().height(80.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141A)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause Button
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = NeonCyan
                )
            }

            Spacer(Modifier.width(16.dp))

            // Time Marker Left
            Text(
                text = currentTimeString,
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            // Slider Immersivo
            Slider(
                value = progress,
                onValueChange = onScrub,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = Color.DarkGray
                )
            )

            // Time Marker Right
            Text(
                text = totalTimeString,
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val s = timeMs / 1000
    val ms = timeMs % 1000
    val m = s / 60
    val sec = s % 60
    return String.format("%02d:%02d.%03d", m, sec, ms)
}
