package com.obelus.presentation.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.local.dao.ScanSessionDao
import com.obelus.data.local.dao.SignalReadingDao
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import com.obelus.data.obd2.ObdReading
import com.obelus.data.repository.ObdRepository
import com.obelus.protocol.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

enum class ScanState {
    IDLE,           // Disconnected/Ready
    CONNECTING,     // Connecting to adapter
    CONNECTED,      // Connected, ready to scan
    SCANNING,       // Loop active
    PAUSED,         // Paused by user
    ERROR,          // Connection error
    DISCONNECTED    // Explicitly disconnected
}

const val CONNECTION_TIMEOUT_MS = 5000L
const val SCAN_DELAY_MS = 50L
const val LOOP_DELAY_MS = 200L
const val PERSISTENCE_DELAY_MS = 1000L

/**
 * ViewModel responsible for managing the OBD2 scanning process,
 * connection state, and data persistence.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ObdRepository,
    private val sessionDao: ScanSessionDao,
    private val readingDao: SignalReadingDao
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val connectionStatus: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(CONNECTION_TIMEOUT_MS), ConnectionState.DISCONNECTED)

    private val _lastReading = MutableStateFlow<ObdReading?>(null)
    val lastReading: StateFlow<ObdReading?> = _lastReading.asStateFlow()

    // Real-time values for UI cards (Key = PID)
    private val _currentReadings = MutableStateFlow<Map<String, ObdReading>>(emptyMap())
    val currentReadings: StateFlow<Map<String, ObdReading>> = _currentReadings.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Default PIDs: RPM, Speed, Temp, Load, Throttle
    private val selectedPids = mutableSetOf("0C", "0D", "05", "04", "11")

    private var scanJob: Job? = null
    
    // Persistence
    private var currentSessionId: Long? = null
    private val readingsBuffer = mutableListOf<SignalReading>()
    private var persistenceJob: Job? = null

    init {
        // Observe connection state to update ScanState
        viewModelScope.launch {
            connectionStatus.collect { status ->
                when (status) {
                    ConnectionState.DISCONNECTED -> {
                        if (_scanState.value != ScanState.IDLE) _scanState.value = ScanState.DISCONNECTED
                        stopScan()
                    }
                    ConnectionState.CONNECTED -> {
                        _scanState.value = ScanState.CONNECTED
                    }
                    else -> {} // Handle others if needed
                }
            }
        }
    }

    fun connect(deviceAddress: String) {
        viewModelScope.launch {
            _scanState.value = ScanState.CONNECTING
            _errorMessage.value = null
            
            try {
                val success = repository.connect(deviceAddress)
                if (!success) {
                    _scanState.value = ScanState.ERROR
                    _errorMessage.value = "Failed to connect to $deviceAddress"
                }
            } catch (e: Exception) {
                _scanState.value = ScanState.ERROR
                _errorMessage.value = "Connection error: ${e.message}"
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
            _scanState.value = ScanState.IDLE // Or disconnected
            _currentReadings.value = emptyMap()
        }
    }

    fun startScan(sessionName: String = "") {
        if (!repository.isConnected()) {
            _errorMessage.value = "Not connected to device"
            return
        }
        
        viewModelScope.launch {
            // Create session in DB
            val session = ScanSession(
                startTime = System.currentTimeMillis(),
                protocol = "CAN", // Detect or hardcode for now
                notes = sessionName.ifEmpty { "Session ${Date()}" },
                isActive = true
            )
            currentSessionId = sessionDao.insertSession(session)
            
            _scanState.value = ScanState.SCANNING
            
            startPersistenceLoop()
            startScanLoop()
        }
    }

    private fun startScanLoop() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            while (isActive && _scanState.value == ScanState.SCANNING) {
                for (pid in selectedPids) {
                    // Check if paused or stopped mid-loop
                    if (_scanState.value != ScanState.SCANNING) break
                    
                    try {
                        val reading = repository.requestPid(pid)
                        if (reading != null) {
                            _lastReading.value = reading
                            updateReadings(reading)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    delay(SCAN_DELAY_MS) 
                }
                delay(LOOP_DELAY_MS) 
            }
        }
    }
    
    private fun startPersistenceLoop() {
        persistenceJob?.cancel()
        persistenceJob = viewModelScope.launch {
            while (isActive && _scanState.value == ScanState.SCANNING) {
                delay(PERSISTENCE_DELAY_MS) // Save every second
                
                currentSessionId?.let { sessionId ->
                    val readingsToSave = synchronized(readingsBuffer) {
                        val list = readingsBuffer.toList()
                        readingsBuffer.clear()
                        list
                    }
                    
                    if (readingsToSave.isNotEmpty()) {
                        readingDao.insertReadings(readingsToSave)
                    }
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        persistenceJob?.cancel()
        
        if (_scanState.value == ScanState.SCANNING) {
             viewModelScope.launch {
                currentSessionId?.let { sessionId ->
                    // Save remaining buffer
                    val readingsToSave = synchronized(readingsBuffer) {
                        val list = readingsBuffer.toList()
                        readingsBuffer.clear()
                        list
                    }
                    if (readingsToSave.isNotEmpty()) {
                        readingDao.insertReadings(readingsToSave)
                    }
                    
                    // Calc stats
                    val avgSpeed = readingDao.getAverageForPid(sessionId, "0D")
                    val maxRpm = readingDao.getMaxForPid(sessionId, "0C")
                    
                    val session = sessionDao.getSessionById(sessionId)
                    session?.let {
                        val updatedSession = it.copy(
                            endTime = System.currentTimeMillis(),
                            isActive = false,
                            averageSpeed = avgSpeed,
                            maxRpm = maxRpm?.toInt()
                        )
                        sessionDao.updateSession(updatedSession)
                    }
                }
                currentSessionId = null
                _scanState.value = ScanState.CONNECTED
            }
        } else {
             // If stopped while not scanning (e.g. disconnected), just ensure state is correct
             if (_scanState.value != ScanState.DISCONNECTED) {
                 _scanState.value = ScanState.CONNECTED
             }
        }
    }
    
    fun pauseScan() {
         scanJob?.cancel()
         persistenceJob?.cancel() // Stop saving while paused
         if (_scanState.value == ScanState.SCANNING) {
            _scanState.value = ScanState.PAUSED
         }
    }

    private fun updateReadings(reading: ObdReading) {
        // Update UI
        _currentReadings.value = _currentReadings.value.toMutableMap().apply {
            put(reading.pid, reading)
        }
        
        // Add to buffer for persistence
        if (_scanState.value == ScanState.SCANNING && currentSessionId != null) {
            synchronized(readingsBuffer) {
                readingsBuffer.add(
                    SignalReading(
                        sessionId = currentSessionId!!,
                        pid = reading.pid,
                        name = reading.name,
                        value = reading.value,
                        unit = reading.unit,
                        timestamp = reading.timestamp
                    )
                )
            }
        }
    }
}
