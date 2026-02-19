package com.obelus.presentation.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.protocol.uds.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State para la pantalla Security Access.
 */
data class SecurityAccessUiState(
    val selectedLevel: SecurityAccessLevel? = null,
    val ecuIdHex: String = "7E0",
    val manufacturer: String = "GENERIC",
    val seedHex: String = "",
    val manualKeyHex: String = "",
    val accessState: SecurityAccessState = SecurityAccessState.Idle,
    val isLoading: Boolean = false,
    val attemptHistory: List<SecurityAccessAttempt> = emptyList(),
    val toastMessage: String? = null,
    val algorithmImplemented: Boolean = false
)

@HiltViewModel
class SecurityAccessViewModel @Inject constructor(
    private val securityRepository: UdsSecurityRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityAccessUiState())
    val uiState: StateFlow<SecurityAccessUiState> = _uiState.asStateFlow()

    val availableLevels: List<SecurityAccessLevel> = securityRepository.availableLevels

    // ── Selección de nivel ──────────────────────────────────────────────────
    fun selectLevel(level: SecurityAccessLevel) {
        val algo = securityRepository.getAlgorithm(
            _uiState.value.manufacturer, level.level
        )
        _uiState.value = _uiState.value.copy(
            selectedLevel    = level,
            seedHex          = "",
            manualKeyHex     = "",
            accessState      = SecurityAccessState.Idle,
            algorithmImplemented = algo.isImplemented
        )
    }

    fun setEcuId(hex: String) {
        _uiState.value = _uiState.value.copy(ecuIdHex = hex.uppercase().take(4))
    }

    fun setManufacturer(mfr: String) {
        _uiState.value = _uiState.value.copy(manufacturer = mfr)
        // Actualizar si hay nivel seleccionado
        _uiState.value.selectedLevel?.let { selectLevel(it) }
    }

    fun setManualKey(key: String) {
        _uiState.value = _uiState.value.copy(manualKeyHex = key)
    }

    // ── Paso 1: Request Seed ────────────────────────────────────────────────
    fun requestSeed() {
        val level = _uiState.value.selectedLevel ?: run {
            showToast("Selecciona un nivel de acceso primero")
            return
        }
        val ecuId = _uiState.value.ecuIdHex.toIntOrNull(16) ?: run {
            showToast("ECU ID inválido")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading   = true,
                accessState = SecurityAccessState.RequestingSeed,
                seedHex     = "",
                manualKeyHex = ""
            )
            val result = securityRepository.requestSeed(ecuId, level.level)
            handleState(result)
        }
    }

    // ── Paso 2: Send Key (manual) ───────────────────────────────────────────
    fun sendKeyManual() {
        val level = _uiState.value.selectedLevel ?: run {
            showToast("Selecciona un nivel de acceso primero")
            return
        }
        if (_uiState.value.seedHex.isEmpty()) {
            showToast("Primero solicita el Seed")
            return
        }
        val keyHex = _uiState.value.manualKeyHex.trim()
        if (keyHex.isEmpty()) {
            showToast("Ingresa la Key calculada")
            return
        }
        val ecuId = _uiState.value.ecuIdHex.toIntOrNull(16) ?: run {
            showToast("ECU ID inválido")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading   = true,
                accessState = SecurityAccessState.SendingKey
            )
            val result = securityRepository.sendKeyHex(ecuId, level.level, keyHex)
            handleState(result)
            refreshHistory()
        }
    }

    // ── Flujo automático (si algoritmo implementado) ────────────────────────
    fun performAutoAccess() {
        val level = _uiState.value.selectedLevel ?: run {
            showToast("Selecciona un nivel de acceso primero")
            return
        }
        val ecuId = _uiState.value.ecuIdHex.toIntOrNull(16) ?: run {
            showToast("ECU ID inválido")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = securityRepository.performAutoAccess(
                ecuId, _uiState.value.manufacturer, level.level
            )
            handleState(result)
            refreshHistory()
        }
    }

    // ── Manejador de estados ────────────────────────────────────────────────
    private fun handleState(state: SecurityAccessState) {
        val toastMsg = when (state) {
            is SecurityAccessState.SeedReceived -> null  // No toast, mostrar en UI
            is SecurityAccessState.AccessGranted ->
                "✅ Acceso concedido — Nivel 0x%02X activo".format(state.level)
            is SecurityAccessState.NegativeResponse -> {
                "❌ NRC 0x%02X: %s\n💡 %s".format(
                    state.rawCode, state.nrc.labelEs, state.nrc.advice
                )
            }
            is SecurityAccessState.Error -> "⚠️ ${state.message}"
            else -> null
        }

        val seedHex = when (state) {
            is SecurityAccessState.SeedReceived -> state.seedHex
            else -> _uiState.value.seedHex
        }

        _uiState.value = _uiState.value.copy(
            isLoading    = false,
            accessState  = state,
            seedHex      = seedHex,
            toastMessage = toastMsg
        )

        toastMsg?.let { showToast(it) }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun refreshHistory() {
        _uiState.value = _uiState.value.copy(
            attemptHistory = securityRepository.attemptHistory
        )
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    fun resetState() {
        _uiState.value = _uiState.value.copy(
            seedHex      = "",
            manualKeyHex = "",
            accessState  = SecurityAccessState.Idle
        )
    }
}
