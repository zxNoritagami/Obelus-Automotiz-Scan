package com.obelus.ui.components.dtc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
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
import com.obelus.ui.theme.DtcCritical
import com.obelus.ui.theme.DtcInfo
import com.obelus.ui.theme.DtcWarning

enum class DtcSeverity(val displayName: String, val color: Color) {
    INFO("INFO", DtcInfo),
    WARNING("WARNING", DtcWarning),
    CRITICAL("CRITICAL", DtcCritical)
}

@Composable
fun DtcSeverityBadge(
    severity: DtcSeverity,
    modifier: Modifier = Modifier
) {
    val icon = when (severity) {
        DtcSeverity.INFO -> Icons.Default.Info
        DtcSeverity.WARNING -> Icons.Default.WarningAmber
        DtcSeverity.CRITICAL -> Icons.Default.Warning
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(severity.color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = severity.displayName,
                tint = severity.color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = severity.displayName,
                color = severity.color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}
