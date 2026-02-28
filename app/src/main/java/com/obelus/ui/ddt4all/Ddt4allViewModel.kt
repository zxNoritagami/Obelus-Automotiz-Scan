package com.obelus.ui.ddt4all

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.ddt.DdtDecoder
import com.obelus.data.ddt.DdtParser
import com.obelus.data.repository.ObdRepository
import com.obelus.domain.model.DdtCommand
import com.obelus.domain.model.DdtEcu
import com.obelus.domain.model.DdtParameter
import com.obelus.domain.model.VehicleHealthSummary
import com.obelus.domain.usecase.GatherVehicleHealthUseCase
import com.obelus.domain.usecase.GenerateHealthPdfReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class Ddt4allViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val obdRepository: ObdRepository,
    private val gatherVehicleHealthUseCase: GatherVehicleHealthUseCase,
    private val generateHealthPdfReportUseCase: GenerateHealthPdfReportUseCase
) : ViewModel() {

    private val ddtParser = DdtParser()
    private val ddtDecoder = DdtDecoder()

    private val _ecuList = MutableStateFlow<List<DdtEcu>>(emptyList())
    val ecuList: StateFlow<List<DdtEcu>> = _ecuList.asStateFlow()

    private val _foundEcus = MutableStateFlow<List<DdtEcu>>(emptyList())
    val foundEcus: StateFlow<List<DdtEcu>> = _foundEcus.asStateFlow()

    private val _selectedEcu = MutableStateFlow<DdtEcu?>(null)
    val selectedEcu: StateFlow<DdtEcu?> = _selectedEcu.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAutoScanning = MutableStateFlow(false)
    val isAutoScanning: StateFlow<Boolean> = _isAutoScanning.asStateFlow()

    private val _healthSummary = MutableStateFlow<VehicleHealthSummary?>(null)
    val healthSummary: StateFlow<VehicleHealthSummary?> = _healthSummary.asStateFlow()

    private val _pdfReportFile = MutableStateFlow<File?>(null)
    val pdfReportFile: StateFlow<File?> = _pdfReportFile.asStateFlow()

    private val _parameterValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val parameterValues: StateFlow<Map<String, String>> = _parameterValues.asStateFlow()

    private val _parameterHistory = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val parameterHistory: StateFlow<Map<String, List<Float>>> = _parameterHistory.asStateFlow()

    private val _showClearDtcConfirm = MutableStateFlow(false)
    val showClearDtcConfirm: StateFlow<Boolean> = _showClearDtcConfirm.asStateFlow()

    private var scanJob: Job? = null
    private val commonAddresses = listOf("7A", "01", "04", "26", "51", "13", "29", "62")

    init {
        loadEcusFromAssets()
    }

    private fun loadEcusFromAssets() {
        viewModelScope.launch {
            _isLoading.value = true
            val ecus = mutableListOf<DdtEcu>()
            withContext(Dispatchers.IO) {
                try {
                    val files = context.assets.list("ddt4all") ?: emptyArray()
                    for (fileName in files) {
                        if (fileName.endsWith(".xml")) {
                            context.assets.open("ddt4all/$fileName").use { inputStream ->
                                ddtParser.parseEcu(inputStream)?.let { ecus.add(it) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Ddt4allViewModel", "Error loading ECUs", e)
                }
            }
            _ecuList.value = ecus
            _isLoading.value = false
        }
    }

    fun generateFullHealthReport() {
        viewModelScope.launch {
            _isLoading.value = true
            val summary = gatherVehicleHealthUseCase.execute(_ecuList.value)
            _healthSummary.value = summary
            
            withContext(Dispatchers.IO) {
                try {
                    val file = generateHealthPdfReportUseCase.execute(context, summary)
                    _pdfReportFile.value = file
                } catch (e: Exception) {
                    Log.e("Ddt4allViewModel", "Error generating PDF", e)
                }
            }
            
            _isLoading.value = false
        }
    }

    fun startAutoScan() {
        if (_isAutoScanning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isAutoScanning.value = true
            val detected = mutableListOf<DdtEcu>()
            if (!obdRepository.isConnected()) {
                _isAutoScanning.value = false
                return@launch
            }
            for (addr in commonAddresses) {
                try {
                    obdRepository.sendCommand("AT SH $addr")
                    val response = obdRepository.sendCommand("22 F1 A0")
                    if (response.isNotEmpty() && !response.contains("ERROR") && !response.contains("NO DATA")) {
                        _ecuList.value.find { it.name.contains(addr, ignoreCase = true) }?.let { detected.add(it) }
                    }
                } catch (e: Exception) { }
                delay(200)
            }
            _foundEcus.value = detected
            _isAutoScanning.value = false
        }
    }

    fun requestClearDtc() {
        _showClearDtcConfirm.value = true
    }

    fun cancelClearDtc() {
        _showClearDtcConfirm.value = false
    }

    fun confirmClearAllDtcs() {
        _showClearDtcConfirm.value = false
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // Modo 04 de OBD2: Clear/Reset Emission-Related Diagnostic Information
                obdRepository.sendCommand("04")
                
                // También intentar borrado UDS por direcciones comunes
                for (addr in commonAddresses) {
                    obdRepository.sendCommand("AT SH $addr")
                    // Service 0x14: ClearDiagnosticInformation (UDS)
                    // FFFF FF -> Borrar todos los grupos
                    obdRepository.sendCommand("14 FF FF FF")
                    delay(100)
                }
                
                // Volver a modo funcional estándar
                obdRepository.sendCommand("AT SH 7E0")
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "DTCs borrados correctamente", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Ddt4allViewModel", "Error clearing DTCs", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectEcu(ecu: DdtEcu) {
        _selectedEcu.value = ecu
        _parameterValues.value = emptyMap()
        _parameterHistory.value = emptyMap()
    }

    fun toggleLiveScan() {
        if (_isScanning.value) stopLiveScan() else startLiveScan()
    }

    private fun startLiveScan() {
        val ecu = _selectedEcu.value ?: return
        if (!obdRepository.isConnected()) return

        _isScanning.value = true
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _isScanning.value) {
                try {
                    val response = obdRepository.sendCommand("22 F1 A0")
                    if (response.isNotEmpty() && !response.contains("ERROR")) {
                        val newValues = mutableMapOf<String, String>()
                        val newHistory = _parameterHistory.value.toMutableMap()

                        ecu.parameters.forEach { param ->
                            val decodedVal = ddtDecoder.decode(response, param)
                            newValues[param.name] = ddtDecoder.getDisplayValue(decodedVal, param)
                            
                            val history = (newHistory[param.name] ?: emptyList()).toMutableList()
                            history.add(decodedVal)
                            if (history.size > 50) history.removeAt(0)
                            newHistory[param.name] = history
                        }
                        _parameterValues.value = newValues
                        _parameterHistory.value = newHistory
                    }
                } catch (e: Exception) { }
                delay(500)
            }
        }
    }

    fun stopLiveScan() {
        _isScanning.value = false
        scanJob?.cancel()
    }

    fun executeCommand(command: DdtCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                obdRepository.sendCommand(command.hexRequest)
            } catch (e: Exception) { }
        }
    }

    fun clearPdfReport() {
        _pdfReportFile.value = null
    }

    fun clearSelectedEcu() {
        stopLiveScan()
        _selectedEcu.value = null
    }
}
