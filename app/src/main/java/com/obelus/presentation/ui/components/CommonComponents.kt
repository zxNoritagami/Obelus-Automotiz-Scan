package com.obelus.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// CommonComponents.kt
// Componentes reutilizables globales del proyecto Obelus.
// ─────────────────────────────────────────────────────────────────────────────

// ══════════════════════════════════════════════════════════════════════════════
// 1. LoadingIndicator
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Spinner centrado con mensaje opcional.
 *
 * @param message   Texto debajo del spinner (null = sin texto).
 * @param size      Diámetro del indicador.
 * @param modifier  Modifier externo.
 */
@Composable
fun LoadingIndicator(
    message: String?    = null,
    size: Dp            = 48.dp,
    modifier: Modifier  = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier      = Modifier.size(size),
            strokeWidth   = (size.value * 0.08f).dp,
            strokeCap     = StrokeCap.Round,
            color         = MaterialTheme.colorScheme.primary,
            trackColor    = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
        message?.let {
            Text(
                text  = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 2. ErrorBanner
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Banner de error con ícono, mensaje y botón opcional de reintentar.
 *
 * @param message       Texto del error a mostrar.
 * @param onRetry       Callback al pulsar "Reintentar" (null = sin botón).
 * @param onDismiss     Callback al cerrar el banner (null = no cerrable).
 */
@Composable
fun ErrorBanner(
    message: String,
    onRetry:   (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier       = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text     = message,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            onRetry?.let {
                TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(
                        "Reintentar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            onDismiss?.let {
                IconButton(onClick = it, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 3. ValueDisplay
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Texto grande para valores numéricos con unidad (monoespaciado).
 *
 * @param value     Texto del valor (ej. "2340").
 * @param unit      Unidad (ej. "rpm", "km/h"). Opcional.
 * @param label     Etiqueta pequeña arriba (ej. "RPM").
 * @param valueColor Color del número (default: onBackground).
 * @param valueSp   Tamaño de la fuente del valor.
 */
@Composable
fun ValueDisplay(
    value: String,
    unit: String?         = null,
    label: String?        = null,
    valueColor: Color     = MaterialTheme.colorScheme.onBackground,
    valueSp: TextUnit     = 40.sp,
    modifier: Modifier    = Modifier
) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        label?.let {
            Text(
                text  = it.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(2.dp))
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text       = value,
                fontSize   = valueSp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color      = valueColor,
                letterSpacing = (-1).sp
            )
            unit?.let {
                Text(
                    text     = it,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 4. SectionDividerHeader (renombrado para no conflicto con ScanScreen privado)
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Título de sección con línea divisora lateral.
 *
 * @param title     Texto del encabezado.
 * @param modifier  Modifier externo.
 */
@Composable
fun SectionDividerHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text       = title.uppercase(),
            style      = MaterialTheme.typography.labelSmall,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            fontSize = 11.sp
        )
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            thickness = 0.8.dp
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "LoadingIndicator")
@Composable
private fun PreviewLoading() {
    MaterialTheme { LoadingIndicator(message = "Conectando al adaptador…", modifier = Modifier.padding(24.dp)) }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ErrorBanner")
@Composable
private fun PreviewErrorBanner() {
    MaterialTheme {
        ErrorBanner(
            message   = "Protocolo no detectado. Verifique el adaptador ELM327.",
            onRetry   = {},
            onDismiss = {},
            modifier  = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "ValueDisplay")
@Composable
private fun PreviewValueDisplay() {
    MaterialTheme {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ValueDisplay(value = "2340", unit = "rpm", label = "RPM", valueColor = Color(0xFF2196F3))
            ValueDisplay(value = "92.0", unit = "°C",  label = "Temp. Motor", valueColor = Color(0xFFFF5252))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "SectionDividerHeader")
@Composable
private fun PreviewSectionHeader() {
    MaterialTheme { SectionDividerHeader(title = "Señales en tiempo real") }
}
