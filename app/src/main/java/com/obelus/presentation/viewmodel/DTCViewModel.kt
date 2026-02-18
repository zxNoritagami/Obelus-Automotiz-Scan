package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.model.DTC
import com.obelus.data.repository.ObdRepository
import com.obelus.protocol.DTCCommand
import com.obelus.protocol.DTCDecoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DTCViewModel @Inject constructor(
    private val obdRepository: ObdRepository
) : ViewModel() {
    
    private val _dtcs = MutableStateFlow<List<DTC>>(emptyList())
    val dtcs: StateFlow<List<DTC>> = _dtcs.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    fun readDTCs() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                // Check connection first
                if (!obdRepository.isConnected()) {
                    // Could handle error here or just return empty
                     _isScanning.value = false
                    return@launch
                }
                
                val response = obdRepository.sendCommand(DTCCommand.GET_CURRENT_DTCS)
                val decodedDtcs = DTCDecoder.decodeDTCs(response)
                // Filter out duplicates if needed, or handle multiple frames response logic if implemented in Data Link Layer
                _dtcs.value = decodedDtcs
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }
    
    fun clearDTCs() {
         viewModelScope.launch {
             if (obdRepository.isConnected()) {
                 obdRepository.sendCommand(DTCCommand.CLEAR_DTCS)
                 _dtcs.value = emptyList() // Clear local list after command
             }
         }
    }
}
