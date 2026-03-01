package com.obelus.cylinder

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obelus.presentation.viewmodel.ScanViewModel
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// CylinderComparisonScreen.kt
// Pantalla completa de comparación de cilindros con gráfico de barras y tabla.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CylinderComparisonScreen(
    onBack: () -> Unit = {},
    viewModel: ScanViewModel = hiltViewModel()
) {
    val balance      by viewModel.cylinderBalance.collectAsStateWithLifecycle()
    val misfireData  by viewModel.misfireData.collectAsStateWithLifecycle()
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()

    var showDiagnosis      by remember { mutableStateOf(false) }
    var showCompressionGuide by remember { mutableStateOf(false) }
    var diagnosis          by remember { mutableStateOf<Diagnosis?>(null) }
    val notSupportedReason by viewModel.cylinderNotSupported.collectAsStateWithLifecycle()

    // Derive precondition readiness from live data
    val rpm         = uiState.readings.firstOrNull { it.pid == "0C" }?.value
    val coolant     = uiState.readings.firstOrNull { it.pid == "05" }?.value
    val rpmOk       = rpm != null && rpm in 550f..1100f
    val warmOk      = coolant != null && coolant >= 75f
    val readyToTest = rpmOk && warmOk

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Comparación de Cilindros",
                            style     = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text("Modo 06 OBD2 · Balance de ralentí",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atrás") } }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top   = padding.calculateTopPadding() + 4.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Preconditions status ──────────────────────────────────────────
            item { PreconditionsCard(rpm, coolant, rpmOk, warmOk) }

            // ── Test button ───────────────────────────────────────────────────
            item {
                Button(
                    onClick  = {
                        showDiagnosis = false
                        diagnosis = null
                        viewModel.runCylinderBalanceTest()
                    },
                    enabled  = readyToTest,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Iniciar test de balance (10–30 s)")
                }
            }

            // ── Bar chart ─────────────────────────────────────────────────────
            balance?.let { b ->
                item { CylinderBarChart(balance = b) }

                // ── Table ─────────────────────────────────────────────────────
                item { CylinderTableHeader() }
                items(b.cylinders) { cyl ->
                    CylinderTableRow(cyl, b.averageContribution, misfireData[cyl.cylinderNumber])
                }

                // ── Actions ───────────────────────────────────────────────────
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick  = {
                                diagnosis = viewModel.identifyWeakCylinder(b)
                                showDiagnosis = true
                            },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Identificar cilindro débil", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick  = { showCompressionGuide = true },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Test compresión", fontSize = 12.sp)
                        }
                    }
                }

                // ── Diagnosis panel ───────────────────────────────────────────
                if (showDiagnosis && diagnosis != null) {
                    item { DiagnosisCard(diagnosis!!) }
                }
            }

            // Modo 06 no soportado
            notSupportedReason?.let { reason ->
                item { NotSupportedCard(reason) }
            }

            // Empty state
            if (balance == null && notSupportedReason == null) {
                item { EmptyCylinderState(readyToTest) }
            }
        }
    }

    // ── Compression guide dialog ──────────────────────────────────────────────
    if (showCompressionGuide) {
        CompressionGuideDialog(onDismiss = { showCompressionGuide = false })
    }
}

// ── Preconditions ─────────────────────────────────────────────────────────────
@Composable
private fun PreconditionsCard(
    rpm: Float?, coolant: Float?,
    rpmOk: Boolean, warmOk: Boolean
) {
    Card(shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Precondiciones del test",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            PrecondRow(
                label = "RPM en ralentí estable (550–1100)",
                ok    = rpmOk,
                value = rpm?.let { "${"%.0f".format(it)} rpm" } ?: "Sin dato"
            )
            PrecondRow(
                label = "Motor caliente (≥ 75 °C)",
                ok    = warmOk,
                value = coolant?.let { "${"%.0f".format(it)} °C" } ?: "Sin dato"
            )
            if (!rpmOk || !warmOk) {
                Text(
                    "⚠ Caliente el motor y estabilice el ralentí ≥ 30 segundos antes de iniciar el test.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFC107)
                )
            }
        }
    }
}

@Composable
private fun PrecondRow(label: String, ok: Boolean, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
            null,
            tint = if (ok) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

// ── Bar chart ─────────────────────────────────────────────────────────────────
@Composable
private fun CylinderBarChart(balance: CylinderBalance) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Contribución al ralentí por cilindro",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = Color(0xFF90CAF9))
            Spacer(Modifier.height(8.dp))

            Canvas(
                modifier = Modifier.fillMaxWidth().height(160.dp)
            ) {
                val cylinders = balance.cylinders
                if (cylinders.isEmpty()) return@Canvas
                val w = size.width; val h = size.height
                val padL = 8f; val padB = 24f; val padT = 8f; val padR = 8f
                val chartW = w - padL - padR; val chartH = h - padT - padB
                val avg = balance.averageContribution

                val allVals = cylinders.mapNotNull { it.contributionToIdle }
                val yMin = (allVals.minOrNull() ?: 70f) - 5f
                val yMax = (allVals.maxOrNull() ?: 130f) + 5f
                val yRange = (yMax - yMin).coerceAtLeast(1f)

                val barSlot = chartW / cylinders.size
                val barW    = barSlot * 0.55f

                // Average reference line
                val avgY = padT + chartH - ((avg - yMin) / yRange) * chartH
                drawLine(Color(0xFF90A4AE), Offset(padL, avgY), Offset(w - padR, avgY),
                    strokeWidth = 1.5f,
                    pathEffect  = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)))

                cylinders.forEachIndexed { i, cyl ->
                    val val_ = cyl.contributionToIdle ?: avg
                    val color = when (cyl.status(avg)) {
                        CylinderStatus.OK      -> Color(0xFF4CAF50)
                        CylinderStatus.MILD    -> Color(0xFFFFC107)
                        CylinderStatus.SEVERE  -> Color(0xFFF44336)
                        CylinderStatus.UNKNOWN -> Color(0xFF78909C)
                    }
                    val barH = ((val_ - yMin) / yRange) * chartH
                    val left = padL + i * barSlot + (barSlot - barW) / 2f
                    val top  = padT + chartH - barH
                    drawRect(color.copy(0.85f), Offset(left, top), Size(barW, barH))

                    // Cylinder number label
                    drawCircle(color.copy(0.3f), barW * 0.4f,
                        Offset(left + barW / 2f, padT + chartH + 12f))
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf(CylinderStatus.OK to "±10%",
                       CylinderStatus.MILD to "±25%",
                       CylinderStatus.SEVERE to ">25%").forEach { (s, lbl) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(cylinderStatusColor(s)))
                        Spacer(Modifier.width(4.dp))
                        Text(lbl, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

private fun cylinderStatusColor(status: CylinderStatus): Color = when (status) {
    CylinderStatus.OK      -> Color(0xFF4CAF50)
    CylinderStatus.MILD    -> Color(0xFFFFC107)
    CylinderStatus.SEVERE  -> Color(0xFFF44336)
    CylinderStatus.UNKNOWN -> Color(0xFF78909C)
}

// ── Table ─────────────────────────────────────────────────────────────────────
@Composable
private fun CylinderTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("CYL", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(36.dp))
        Text("CONTRIB.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text("DESV.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text("MISF.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text("ESTADO", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(62.dp), textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

@Composable
private fun CylinderTableRow(cyl: CylinderData, avg: Float, misfireOverride: Int?) {
    val status  = cyl.status(avg)
    val color   = cylinderStatusColor(status)
    val devPct  = cyl.deviationPercent(avg)
    val misfires = misfireOverride ?: cyl.misfireCount

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cylinder number badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.18f))
        ) {
            Text("${cyl.cylinderNumber}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = color)
        }
        Spacer(Modifier.width(8.dp))

        Text(
            cyl.contributionToIdle?.let { "${"%.1f".format(it)}%" } ?: "—",
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold
        )
        Text(
            devPct?.let {
                val sign = if (it >= 0f) "+" else ""; "$sign${"%.1f".format(it)}%"
            } ?: "—",
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = if (devPct != null && abs(devPct) > 10f) color else MaterialTheme.colorScheme.outline
        )
        Text(
            misfires?.toString() ?: "—",
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = if ((misfires ?: 0) > 0) Color(0xFFF44336) else MaterialTheme.colorScheme.outline
        )
        Surface(
            shape  = RoundedCornerShape(4.dp),
            color  = color.copy(alpha = 0.18f),
            modifier = Modifier.width(62.dp)
        ) {
            Text(
                status.name, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                color    = color, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

// ── Diagnosis card ────────────────────────────────────────────────────────────
@Composable
private fun DiagnosisCard(diagnosis: Diagnosis) {
    val (bgColor, iconColor) = when (diagnosis.severity) {
        Severity.OK     -> Color(0x1A4CAF50) to Color(0xFF4CAF50)
        Severity.MILD   -> Color(0x1AFFC107) to Color(0xFFFFC107)
        Severity.SEVERE -> Color(0x1AF44336) to Color(0xFFF44336)
    }
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (diagnosis.severity) {
                        Severity.OK     -> Icons.Default.CheckCircle
                        Severity.MILD   -> Icons.Default.Warning
                        Severity.SEVERE -> Icons.Default.Error
                    },
                    null, tint = iconColor, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(diagnosis.summary, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium, color = iconColor)
            }

            if (diagnosis.probableCauses.isNotEmpty()) {
                Text("Causas probables:", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                diagnosis.probableCauses.forEach { cause ->
                    Row(Modifier.padding(start = 8.dp)) {
                        Text("•  ", style = MaterialTheme.typography.bodySmall)
                        Text(cause, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (diagnosis.suggestedTests.isNotEmpty()) {
                Text("Tests recomendados:", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                diagnosis.suggestedTests.forEachIndexed { i, test ->
                    Row(Modifier.padding(start = 8.dp)) {
                        Text("${i + 1}. ", style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold, color = iconColor)
                        Text(test, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ── Compression guide dialog ───────────────────────────────────────────────────
@Composable
private fun CompressionGuideDialog(onDismiss: () -> Unit) {
    val steps = listOf(
        "Asegúrese de que el motor esté caliente (≥ 75°C).",
        "Desconecte todos los inyectores o la bomba de combustible.",
        "Retire la bujía del cilindro a probar.",
        "Instale el adaptador del manómetro de compresión.",
        "Con el acelerador a fondo, gire el motor 5-6 vueltas (arranque).",
        "Anote la presión máxima alcanzada (mín: 1000 kPa ≈ 145 PSI).",
        "Repita para cada cilindro y compare (diferencia máx: 10%).",
        "Diferencia > 10% → inspeccionar válvulas, anillos y junta de culata."
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Guía de Test de Compresión", fontWeight = FontWeight.Bold) },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Procedimiento manual (no automatizable vía OBD2):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline)
                steps.forEachIndexed { i, step ->
                    Row {
                        Text("${i + 1}. ", fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary)
                        Text(step, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido") }
        }
    )
}

// ── Not Supported card ────────────────────────────────────────────────────────
@Composable
private fun NotSupportedCard(reason: String) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFA726)),
        border = BorderStroke(1.dp, Color(0xFFFFA726).copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    "Modo 06 no soportado",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFA726)
                )
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyCylinderState(readyToTest: Boolean) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.BarChart, null,
                modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            Text(
                if (readyToTest) "Pulsa \"Iniciar test de balance\" para comenzar"
                else             "Caliente el motor y estabilice el ralentí para iniciar",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
