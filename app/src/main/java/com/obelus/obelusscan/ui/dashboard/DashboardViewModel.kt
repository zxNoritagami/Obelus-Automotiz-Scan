package com.obelus.obelusscan.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.analysis.stream.DataStreamAnalyzer
import com.obelus.analysis.stream.ObdDataPoint
import com.obelus.data.local.dao.ScanSessionDao
import com.obelus.data.local.dao.SignalReadingDao
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import com.obelus.data.repository.ObdRepository
import com.obelus.data.repository.TelemetryRepository
import com.obelus.domain.model.ObdPid
import com.obelus.obelusscan.data.protocol.ObdProtocol
import com.obelus.protocol.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecordingState { IDLE, RECORDING, SAVED }

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ObdRepository,
    private val telemetryRepository: TelemetryRepository,
    private val streamAnalyzer: DataStreamAnalyzer,
    private val sessionDao: ScanSessionDao,
    private val signalDao: SignalReadingDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val protocol = ObdProtocol()
    private var scanJob: Job? = null
    private var connectionWatchJob: Job? = null

    // --- StateFlows para Grabación ---
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private var currentSessionId: Long = -1

    init {
        startConnectionWatch()
    }

    private fun startConnectionWatch() {
        connectionWatchJob = viewModelScope.launch {
            repository.connectionState.collect { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        // telemetryRepository.startTelemetry(dtcCount = 0)
                    }
                    ConnectionState.DISCONNECTED,
                    ConnectionState.ERROR -> {
                        // telemetryRepository.stopTelemetry()
                    }
                    else -> { /* CONNECTING */ }
                }
            }
        }
    }

    // --- StateFlows para PIDs ---
    private val _rpm = MutableStateFlow(0f)
    val rpm: StateFlow<Float> = _rpm.asStateFlow()

    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _coolantTemp = MutableStateFlow(0f)
    val coolantTemp: StateFlow<Float> = _coolantTemp.asStateFlow()

    private val _engineLoad = MutableStateFlow(0f)
    val engineLoad: StateFlow<Float> = _engineLoad.asStateFlow()

    private val _throttlePos = MutableStateFlow(0f)
    val throttlePos: StateFlow<Float> = _throttlePos.asStateFlow()

    private val _mafRate = MutableStateFlow(0f)
    val mafRate: StateFlow<Float> = _mafRate.asStateFlow()

    // --- StateFlows para Anomalías ---
    private val _rpmAnomaly = MutableStateFlow(false)
    val rpmAnomaly: StateFlow<Boolean> = _rpmAnomaly.asStateFlow()

    private val _mafAnomaly = MutableStateFlow(false)
    val mafAnomaly: StateFlow<Boolean> = _mafAnomaly.asStateFlow()

    private val _tempAnomaly = MutableStateFlow(false)
    val tempAnomaly: StateFlow<Boolean> = _tempAnomaly.asStateFlow()

    private val _loadAnomaly = MutableStateFlow(false)
    val loadAnomaly: StateFlow<Boolean> = _loadAnomaly.asStateFlow()

    private val _throttleAnomaly = MutableStateFlow(false)
    val throttleAnomaly: StateFlow<Boolean> = _throttleAnomaly.asStateFlow()

    fun startScanning() {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            var loopCounter = 0

            while (isActive) {
                updatePid(ObdPid.RPM, _rpm, _rpmAnomaly)
                updatePid(ObdPid.SPEED, _speed)

                if (loopCounter % 2 == 0) {
                    updatePid(ObdPid.COOLANT_TEMP, _coolantTemp, _tempAnomaly)
                    updatePid(ObdPid.ENGINE_LOAD, _engineLoad, _loadAnomaly)
                    updatePid(ObdPid.THROTTLE_POS, _throttlePos, _throttleAnomaly)
                    updatePid(ObdPid.MAF_RATE, _mafRate, _mafAnomaly)
                }
                
                delay(500)
                loopCounter++
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
    }

    // --- Lógica de Grabación ---

    fun toggleRecording() {
        if (_recordingState.value == RecordingState.RECORDING) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = ScanSession(
                startTime = System.currentTimeMillis()
            )
            currentSessionId = sessionDao.insert(session)
            _recordingState.update { RecordingState.RECORDING }
        }
    }

    private fun stopRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            if (currentSessionId != -1L) {
                val session = sessionDao.getById(currentSessionId)
                session?.let {
                    sessionDao.update(it.copy(
                        endTime = System.currentTimeMillis(),
                        isActive = false
                    ))
                }
            }
            _recordingState.value = RecordingState.SAVED
            delay(2000)
            _recordingState.value = RecordingState.IDLE
        }
    }

    private suspend fun requestPid(pid: ObdPid): Float? {
        if (!repository.isConnected()) return null

        return try {
            val command = protocol.buildCommand(pid)
            val rawResponse = repository.sendCommand(command)
            
            if (rawResponse.contains("NO DATA") || rawResponse.contains("ERROR")) {
                null
            } else {
                protocol.parseResponse(pid, rawResponse)
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updatePid(pid: ObdPid, flow: MutableStateFlow<Float>, anomalyFlow: MutableStateFlow<Boolean>? = null) {
        val value = requestPid(pid)
        if (value != null) {
            flow.value = value
            
            streamAnalyzer.addDataPoint(ObdDataPoint(pid.pidCode, value.toDouble()))
            anomalyFlow?.value = streamAnalyzer.analyzeAnomaly(pid.pidCode)?.isAnomalous ?: false
            
            if (_recordingState.value == RecordingState.RECORDING && currentSessionId != -1L) {
                signalDao.insert(SignalReading(
                    sessionId = currentSessionId,
                    pid = pid.pidCode,
                    name = pid.description,
                    value = value,
                    unit = pid.unit,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
    }
}
