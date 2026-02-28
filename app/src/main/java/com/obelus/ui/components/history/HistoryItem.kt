package com.obelus.ui.components.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.ui.theme.NeonCyan

enum class HistoryItemType { RACE, LOG, DTC }

@Composable
fun HistoryItem(
    title: String,
    subtitle: String,
    value: String,
    type: HistoryItemType,
    isPr: Boolean = false,
    onClick: () -> Unit
) {
    val BgPanel = Color(0xFF161B22)
    val TextPrimary = Color(0xFFE6EDF3)
    val TextMuted = Color(0xFF8B949E)
    val Gold = Color(0xFFFFD700)

    val iconBaseTint = when (type) {
        HistoryItemType.RACE -> NeonCyan
        HistoryItemType.LOG -> Color(0xFFBC8CFF)
        HistoryItemType.DTC -> Color(0xFFF85149)
    }

    val icon = when (type) {
        HistoryItemType.RACE -> Icons.Default.Flag
        HistoryItemType.LOG -> Icons.Default.ReceiptLong
        HistoryItemType.DTC -> Icons.Default.WarningAmber
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BgPanel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBaseTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconBaseTint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (isPr && type == HistoryItemType.RACE) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.EmojiEvents, contentDescription = "Personal Record", tint = Gold, modifier = Modifier.size(16.dp))
                        Text(" PR", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = TextMuted, fontSize = 13.sp)
            }
            Text(value, color = if (isPr) Gold else NeonCyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}
