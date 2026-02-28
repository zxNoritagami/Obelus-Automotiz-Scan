package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.protocol.uds.UdsSecurityRepository
import com.obelus.data.protocol.uds.SecurityAccessState
import com.obelus.data.protocol.uds.SecurityAccessLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class SecurityAccessViewModel @Inject constructor(
    private val securityRepository: UdsSecurityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityAccessUiState())
    val uiState: StateFlow<SecurityAccessUiState> = _uiState.asStateFlow()

    val availableLevels: List<SecurityAccessLevel> = securityRepository.availableLevels

    fun setEcuId(hexId: String) {
        _uiState.update { it.copy(ecuIdHex = hexId) }
    }

    fun setManufacturer(mfr: String) {
        _uiState.update { it.copy(manufacturer = mfr) }
    }

    fun selectLevel(level: SecurityAccessLevel) {
        _uiState.update { it.copy(selectedLevel = level, seedHex = "", manualKeyHex = "") }
    }

    fun setManualKey(key: String) {
        _uiState.update { it.copy(manualKeyHex = key) }
    }

    fun requestSeed() {
        val level = _uiState.value.selectedLevel?.level ?: return
        val ecuId = _uiState.value.ecuIdInt
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, accessState = SecurityAccessState.RequestingSeed) }
            val state = securityRepository.requestSeed(ecuId, level)
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    accessState = state,
                    seedHex = if (state is SecurityAccessState.SeedReceived) state.seedHex else "",
                    attemptHistory = securityRepository.attemptHistory
                )
            }
        }
    }

    fun sendKeyManual() {
        val state = _uiState.value.accessState
        if (state !is SecurityAccessState.SeedReceived) return
        
        val keyHex = _uiState.value.manualKeyHex
        val ecuId = _uiState.value.ecuIdInt
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, accessState = SecurityAccessState.SendingKey) }
            
            val finalState = securityRepository.sendKeyHex(ecuId, state.level, keyHex)
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    accessState = finalState,
                    attemptHistory = securityRepository.attemptHistory
                ) 
            }
        }
    }

    fun performAutoAccess() {
        val level = _uiState.value.selectedLevel ?: return
        val ecuId = _uiState.value.ecuIdInt
        val manufacturer = _uiState.value.manufacturer
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val finalState = securityRepository.performAutoAccess(ecuId, manufacturer, level.level)
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    accessState = finalState,
                    seedHex = if (finalState is SecurityAccessState.SeedReceived) finalState.seedHex else it.seedHex,
                    attemptHistory = securityRepository.attemptHistory
                )
            }
        }
    }
}

// Extension para convertir hex string a Int de forma segura
val SecurityAccessUiState.ecuIdInt: Int
    get() = try { ecuIdHex.toInt(16) } catch (e: Exception) { 0x7E0 }
