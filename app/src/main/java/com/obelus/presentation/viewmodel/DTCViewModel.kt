package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.model.DTC
import com.obelus.data.repository.EnrichedDtc
import com.obelus.data.repository.ManufacturerDtcRepository
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
    private val obdRepository: ObdRepository,
    private val manufacturerDtcRepository: ManufacturerDtcRepository
) : ViewModel() {

    private val _dtcs = MutableStateFlow<List<DTC>>(emptyList())
    val dtcs: StateFlow<List<DTC>> = _dtcs.asStateFlow()

    /** DTCs enriquecidos con info de fabricante — es lo que muestra la UI */
    private val _enrichedDtcs = MutableStateFlow<List<EnrichedDtc>>(emptyList())
    val enrichedDtcs: StateFlow<List<EnrichedDtc>> = _enrichedDtcs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isEnriching = MutableStateFlow(false)
    val isEnriching: StateFlow<Boolean> = _isEnriching.asStateFlow()

    private val _detectedManufacturer = MutableStateFlow("GENERIC")
    val detectedManufacturer: StateFlow<String> = _detectedManufacturer.asStateFlow()

    init {
        // Asegurar que la DB de DTCs de fabricante está inicializada
        viewModelScope.launch {
            manufacturerDtcRepository.ensureDatabaseSeeded()
        }
    }

    fun readDTCs() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                if (!obdRepository.isConnected()) {
                    _isScanning.value = false
                    return@launch
                }

                // 1. Leer DTCs OBD2 raw
                val response     = obdRepository.sendCommand(DTCCommand.GET_CURRENT_DTCS)
                val decodedDtcs  = DTCDecoder.decodeDTCs(response)
                _dtcs.value      = decodedDtcs

                // 2. Intentar leer VIN para detectar fabricante
                tryDetectManufacturer()

                // 3. Enriquecer DTCs con info de fabricante
                if (decodedDtcs.isNotEmpty()) {
                    _isEnriching.value = true
                    val enriched = manufacturerDtcRepository.enrichDtcs(
                        dtcs         = decodedDtcs,
                        manufacturer = _detectedManufacturer.value
                    )
                    _enrichedDtcs.value = enriched
                    _isEnriching.value = false
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
                _isEnriching.value = false
            }
        }
    }

    fun clearDTCs() {
        viewModelScope.launch {
            if (obdRepository.isConnected()) {
                obdRepository.sendCommand(DTCCommand.CLEAR_DTCS)
                _dtcs.value = emptyList()
                _enrichedDtcs.value = emptyList()
            }
        }
    }

    /**
     * Permite seleccionar el fabricante manualmente desde la UI.
     * Útil si el VIN no es legible o el usuario sabe de qué marca es.
     * Re-enriquece los DTCs actuales con el nuevo fabricante.
     */
    fun selectManufacturer(manufacturer: String) {
        _detectedManufacturer.value = manufacturer
        manufacturerDtcRepository.setManufacturer(manufacturer)
        viewModelScope.launch {
            if (_dtcs.value.isNotEmpty()) {
                _isEnriching.value = true
                _enrichedDtcs.value = manufacturerDtcRepository.enrichDtcs(
                    dtcs         = _dtcs.value,
                    manufacturer = manufacturer
                )
                _isEnriching.value = false
            }
        }
    }

    private suspend fun tryDetectManufacturer() {
        try {
            val vinResponse = obdRepository.sendCommand("0902") // PID VIN
            if (vinResponse.isNotBlank() && vinResponse.length >= 17) {
                val vin = vinResponse.trim()
                val manufacturer = manufacturerDtcRepository.detectManufacturerFromVin(vin)
                _detectedManufacturer.value = manufacturer
            }
        } catch (e: Exception) {
            // VIN no disponible — quedarse con GENERIC
        }
    }
}
