package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.local.entity.DtcCode
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import com.obelus.data.repository.ObelusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ObelusRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _currentSession = MutableStateFlow<ScanSession?>(null)
    val currentSession: StateFlow<ScanSession?> = _currentSession.asStateFlow()

    private val _readings = MutableStateFlow<List<SignalReading>>(emptyList())
    val readings: StateFlow<List<SignalReading>> = _readings.asStateFlow()

    private val _dtcs = MutableStateFlow<List<DtcCode>>(emptyList())
    val dtcs: StateFlow<List<DtcCode>> = _dtcs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun startScan(sessionName: String) {
        viewModelScope.launch {
            try {
                // Determine protocol or other settings from logic (hardcoded or passed in)
                val newSession = ScanSession(
                    startTime = System.currentTimeMillis(),
                    protocol = "CAN_11BIT_500K", // Example default
                    notes = sessionName
                )
                repository.startSession(newSession)
                _currentSession.value = newSession
                _scanState.value = ScanState.SCANNING
            } catch (e: Exception) {
                _scanState.value = ScanState.ERROR
                _errorMessage.value = "Failed to start scan: ${e.message}"
            }
        }
    }

    fun stopScan() {
        viewModelScope.launch {
            try {
                _currentSession.value?.let { session ->
                    val endedSession = session.copy(endTime = System.currentTimeMillis())
                    repository.endSession(endedSession)
                    _currentSession.value = null
                }
                _scanState.value = ScanState.IDLE
            } catch (e: Exception) {
                _errorMessage.value = "Error stopping scan: ${e.message}"
            }
        }
    }

    fun pauseScan() {
        if (_scanState.value == ScanState.SCANNING) {
            _scanState.value = ScanState.PAUSED
        }
    }

    fun resumeScan() {
        if (_scanState.value == ScanState.PAUSED) {
            _scanState.value = ScanState.SCANNING
        }
    }

    fun onReadingReceived(canId: String, rawData: ByteArray) {
        if (_scanState.value != ScanState.SCANNING) return

        viewModelScope.launch {
             // In a real app, logic to decode rawData using signals matching canId would go here
             // For now, we simulate saving a reading if we have an active session
             _currentSession.value?.let { session ->
                 // Example placeholder reading
                 // val reading = SignalReading(...)
                 // repository.saveReading(reading)
                 // Then update _readings flow
             }
        }
    }

    fun clearError() {
        _errorMessage.value = null
        if (_scanState.value == ScanState.ERROR) {
            _scanState.value = ScanState.IDLE
        }
    }
}
