package com.obelus.presentation.viewmodel

import com.obelus.domain.model.DiagnosticFinding
import com.obelus.domain.model.FreezeFrameData

/**
 * Representa el estado de la interfaz de usuario para la pantalla de diagnóstico.
 */
data class DiagnosticUiState(
    val findings: List<DiagnosticFinding> = emptyList(),
    val topFinding: DiagnosticFinding? = null,
    val vehicleHealthScore: Int = 100,
    val hasCriticalAlert: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedFreezeFrame: FreezeFrameData? = null
)
