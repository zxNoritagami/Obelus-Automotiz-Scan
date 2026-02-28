package com.obelus.presentation.viewmodel

import com.obelus.data.protocol.uds.SecurityAccessLevel
import com.obelus.data.protocol.uds.SecurityAccessState
import com.obelus.data.protocol.uds.SecurityAccessAttempt

/**
 * Estado de la interfaz de usuario para la pantalla de Security Access.
 */
data class SecurityAccessUiState(
    val ecuIdHex: String = "7E0",
    val manufacturer: String = "GENERIC",
    val selectedLevel: SecurityAccessLevel? = null,
    val seedHex: String = "",
    val manualKeyHex: String = "",
    val isLoading: Boolean = false,
    val accessState: SecurityAccessState = SecurityAccessState.Idle,
    val attemptHistory: List<SecurityAccessAttempt> = emptyList(),
    val algorithmImplemented: Boolean = false
)
