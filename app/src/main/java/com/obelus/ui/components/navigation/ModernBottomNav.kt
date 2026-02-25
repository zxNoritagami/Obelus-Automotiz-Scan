package com.obelus.ui.components.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.obelus.ui.theme.DarkBackground
import com.obelus.ui.theme.NeonCyan
import com.obelus.ui.theme.TextSecondary
import com.obelus.ui.theme.FuturisticTypography

data class NavItem(
    val route: String,
    val title: String,
    val iconFilled: ImageVector,
    val iconOutlined: ImageVector
)

@Composable
fun ModernBottomNav(
    items: List<NavItem>,
    currentRoute: String?,
    onItemClick: (String) -> Unit
) {
    Surface(
        color = DarkBackground,
        contentColor = TextSecondary,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                
                val alpha by animateFloatAsState(if (selected) 1f else 0.5f, label = "alpha")
                val scale by animateFloatAsState(if (selected) 1.1f else 1f, label = "scale")
                
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { onItemClick(item.route) },
                        modifier = Modifier
                            .scale(scale)
                            .clip(if (selected) RoundedCornerShape(12.dp) else CircleShape)
                            .background(if (selected) NeonCyan.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (selected) item.iconFilled else item.iconOutlined,
                                contentDescription = item.title,
                                tint = if (selected) NeonCyan else TextSecondary.copy(alpha = alpha),
                                modifier = Modifier.size(24.dp)
                            )
                            AnimatedVisibility(visible = selected) {
                                Text(
                                    text = item.title,
                                    style = FuturisticTypography.labelSmall,
                                    color = NeonCyan,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
