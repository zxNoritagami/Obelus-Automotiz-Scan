package com.obelus.cylinder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// CylinderBalanceCard.kt
// Componente compacto para mostrar en el dashboard de escaneo.
// ─────────────────────────────────────────────────────────────────────────────

private fun cylinderColor(status: CylinderStatus): Color = when (status) {
    CylinderStatus.OK      -> Color(0xFF4CAF50)
    CylinderStatus.MILD    -> Color(0xFFFFC107)
    CylinderStatus.SEVERE  -> Color(0xFFF44336)
    CylinderStatus.UNKNOWN -> Color(0xFF78909C)
}

/**
 * Tarjeta compacta con mini gráfico de barras de cilindros.
 *
 * @param balance   Resultado del test (null = sin datos / no ejecutado).
 * @param onClick   Navegar a la pantalla detalle.
 */
@Composable
fun CylinderBalanceCard(
    balance: CylinderBalance?,
    onClick: () -> Unit = {}
) {
    val hasData = balance != null && balance.cylinders.isNotEmpty()
    val balanced = balance?.isBalanced ?: true
    val borderColor = when {
        !hasData    -> Color.Transparent
        !balanced   -> Color(0xFFF44336).copy(0.7f)
        else        -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(12.dp),
        border   = if (!balanced) BorderStroke(1.dp, borderColor) else null,
        colors   = CardDefaults.cardColors(
            containerColor = if (!balanced) Color(0x1AF44336)
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            // Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (!balanced) Color(0xFFF44336).copy(alpha = 0.15f)
                        else Color(0xFF1565C0).copy(alpha = 0.15f)
                    )
            ) {
                Icon(
                    if (!balanced) Icons.Default.Warning else Icons.Default.BarChart,
                    null,
                    tint = if (!balanced) Color(0xFFF44336) else Color(0xFF42A5F5),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "Balance de cilindros",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (hasData && balance != null) {
                    Text(
                        balance.let { b ->
                            if (b.isBalanced) "Balanceado — desviación ${b.imbalancePercent.toInt()}%"
                            else "Desbalance detectado — cilindro #${b.weakestCylinder} (${b.imbalancePercent.toInt()}%)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (balanced) MaterialTheme.colorScheme.outline else Color(0xFFF44336)
                    )
                } else {
                    Text("Sin datos — pulsa para iniciar test",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }

            // Mini bar chart (max 6 cylinders visible)
            if (hasData && balance != null) {
                MiniBarChart(
                    balance   = balance,
                    modifier  = Modifier.width(80.dp).height(36.dp)
                )
            }
        }
    }
}

// ── Mini bar chart ────────────────────────────────────────────────────────────
@Composable
fun MiniBarChart(balance: CylinderBalance, modifier: Modifier = Modifier) {
    val cylinders = balance.cylinders.take(8)
    Canvas(modifier = modifier) {
        val avg  = balance.averageContribution
        val w    = size.width;  val h = size.height
        val barW = (w / cylinders.size) * 0.6f
        val gap  = (w / cylinders.size) * 0.4f / 2f

        // Average line
        drawLine(Color(0xFF90A4AE), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.5f)

        cylinders.forEachIndexed { i, cyl ->
            val contrib = cyl.contributionToIdle ?: avg
            val status  = cyl.status(avg)
            val color   = cylinderColor(status)

            // Normalize bar height
            val deviation = (contrib - avg) / avg.coerceAtLeast(1f)
            val barH = h * 0.4f * (1f + deviation).coerceIn(0.05f, 2f)
            val left = i * (w / cylinders.size) + gap
            val top  = h / 2f - barH / 2f

            drawRect(color.copy(alpha = 0.85f), Offset(left, top), Size(barW, barH))
        }
    }
}
