package com.obelus.ui.components.race

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.ui.theme.DeepSurface
import com.obelus.ui.theme.NeonAmber
import com.obelus.ui.theme.NeonCyan
import com.obelus.ui.theme.RaceAccent

@Composable
fun ResultCard(
    title: String,
    primaryTime: String,
    isPersonalBest: Boolean,
    maxG: Float,
    estimatedHp: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DeepSurface.copy(alpha = 0.9f),
                        DeepSurface.copy(alpha = 0.7f)
                    )
                )
            )
            .border(
                width = if (isPersonalBest) 2.dp else 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isPersonalBest) {
                        listOf(NeonCyan, RaceAccent)
                    } else {
                        listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isPersonalBest) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = RaceAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "NEW PERSONAL BEST!",
                        color = RaceAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Star, contentDescription = null, tint = RaceAccent)
                }
            } else {
                Text(
                    text = title.uppercase(),
                    color = Color.Gray,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Text(
                text = primaryTime,
                fontSize = 56.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = if (isPersonalBest) NeonCyan else Color.White
            )

            Spacer(Modifier.height(16.dp))

            // Sub Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ResultStat(label = "MAX G", value = String.format("%.2f", maxG), unit = "G")
                ResultStat(label = "POWER", value = if (estimatedHp > 0) String.format("%.0f", estimatedHp) else "—", unit = "HP")
            }
        }
    }
}

@Composable
fun ResultStat(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, fontSize = 12.sp, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(text = " $unit", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}
