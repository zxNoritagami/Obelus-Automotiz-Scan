package com.obelus.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obelus.domain.model.ActuatorCategory
import com.obelus.domain.model.ActuatorTest
import com.obelus.presentation.ui.components.SafetyWarningDialog
import com.obelus.presentation.viewmodel.ActuatorTestViewModel
import com.obelus.presentation.viewmodel.TestExecutionState

@Composable
fun ActuatorTestScreen(
    viewModel: ActuatorTestViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Safety dialog
    val safetyWarning = state.pendingSafetyConfirmation
    if (safetyWarning != null) {
        SafetyWarningDialog(
            safetyWarning = safetyWarning,
            onConfirm = { viewModel.confirmSafety() },
            onDismiss = { viewModel.dismissSafety() }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Status Banner (running / result) ──────────────────────────────────
        AnimatedVisibility(
            visible = state.executionState != TestExecutionState.IDLE,
            enter = fadeIn(), exit = fadeOut()
        ) {
            StatusBanner(viewModel, state.executionState, state.currentTestId,
                state.progressMessage, state.elapsedMs, state.lastParsedValue,
                state.errorMessage)
        }

        // ── Test list grouped by category ─────────────────────────────────────
        if (state.testsByCategory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.testsByCategory.entries.sortedBy { it.key.ordinal }.forEach { (category, tests) ->
                    item {
                        CategoryHeader(category)
                    }
                    items(tests, key = { it.id }) { test ->
                        ActuatorTestCard(
                            test = test,
                            isRunning = state.executionState == TestExecutionState.RUNNING &&
                                state.currentTestId == test.id,
                            isSuccess = state.executionState == TestExecutionState.SUCCESS &&
                                state.currentTestId == test.id,
                            isFailed  = (state.executionState == TestExecutionState.FAILED ||
                                state.executionState == TestExecutionState.ERROR) &&
                                state.currentTestId == test.id,
                            onExecute = { viewModel.requestTest(test.id) },
                            onStop    = { viewModel.stopTest() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: ActuatorCategory) {
    val (emoji, color) = when (category) {
        ActuatorCategory.ENGINE    -> "🔧" to MaterialTheme.colorScheme.primary
        ActuatorCategory.FUEL      -> "⛽" to Color(0xFFFF6F00)
        ActuatorCategory.COOLING   -> "🌡" to Color(0xFF0288D1)
        ActuatorCategory.ELECTRICAL -> "⚡" to Color(0xFFF9A825)
        ActuatorCategory.EMISSIONS -> "💨" to Color(0xFF388E3C)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            text = category.displayName.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActuatorTestCard(
    test: ActuatorTest,
    isRunning: Boolean,
    isSuccess: Boolean,
    isFailed: Boolean,
    onExecute: () -> Unit,
    onStop: () -> Unit
) {
    val borderColor = when {
        isSuccess -> MaterialTheme.colorScheme.primary
        isFailed  -> MaterialTheme.colorScheme.error
        isRunning -> MaterialTheme.colorScheme.secondary
        else      -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (borderColor != Color.Transparent)
            androidx.compose.foundation.BorderStroke(2.dp, borderColor)
        else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(test.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "CMD: ${test.command}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                // Status icon
                when {
                    isRunning -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    isSuccess -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    isFailed  -> Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                test.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Safety badge
            if (test.safetyWarning != null) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Requiere confirmación de seguridad",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action button
            Button(
                onClick = if (isRunning) onStop else onExecute,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isRunning) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ) else ButtonDefaults.buttonColors(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isRunning) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Detener test")
                } else {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ejecutar")
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(
    viewModel: ActuatorTestViewModel,
    executionState: TestExecutionState,
    currentTestId: String?,
    progressMessage: String?,
    elapsedMs: Long,
    lastParsedValue: String?,
    errorMessage: String?
) {
    val (bgColor, contentColor) = when (executionState) {
        TestExecutionState.RUNNING -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        TestExecutionState.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        TestExecutionState.FAILED,
        TestExecutionState.ERROR   -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
        else                       -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (executionState) {
                TestExecutionState.RUNNING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = contentColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(progressMessage ?: "Ejecutando...", style = MaterialTheme.typography.bodyMedium, color = contentColor)
                    }
                    Text("Tiempo: ${elapsedMs}ms", style = MaterialTheme.typography.labelSmall, color = contentColor)
                }
                TestExecutionState.SUCCESS -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = contentColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Resultado: $lastParsedValue", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold, color = contentColor)
                    }
                    Text("Tiempo de respuesta: ${elapsedMs}ms", style = MaterialTheme.typography.labelSmall, color = contentColor)
                }
                TestExecutionState.FAILED,
                TestExecutionState.ERROR -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = contentColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(errorMessage ?: "Error desconocido", style = MaterialTheme.typography.bodyMedium, color = contentColor)
                    }
                }
                else -> {}
            }
            // Dismiss
            TextButton(
                onClick = { viewModel.loadTests() },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Cerrar", color = contentColor, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
