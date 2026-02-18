package com.obelus.obelusscan.ui.race

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.repository.ObdRepository
import com.obelus.domain.model.ObdPid
import com.obelus.obelusscan.data.protocol.ObdProtocol
import com.obelus.obelusscan.domain.model.RaceSession
import com.obelus.obelusscan.domain.model.RaceState
import com.obelus.obelusscan.domain.model.RaceType
import com.obelus.obelusscan.domain.model.SplitTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class RaceModeViewModel @Inject constructor(
    private val repository: ObdRepository
) : ViewModel() {

    private val protocol = ObdProtocol()
    private var raceJob: Job? = null

    // Estados UI
    private val _raceState = MutableStateFlow(RaceState.IDLE)
    val raceState: StateFlow<RaceState> = _raceState.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0)
    val currentSpeed: StateFlow<Int> = _currentSpeed.asStateFlow()

    private val _session = MutableStateFlow<RaceSession?>(null)
    val session: StateFlow<RaceSession?> = _session.asStateFlow()

    // Variables internas de control
    private var startTimestamp: Long = 0
    private var lastSpeed: Int = 0
    private var lastTime: Long = 0

    // Configuración actual
    private var currentRaceType: RaceType = RaceType.ACCELERATION_0_100

    /**
     * Prepara el sistema para una carrera.
     * El estado pasa a ARMED.
     */
    fun armRace(type: RaceType) {
        if (!repository.isConnected()) return // O manejar error UI

        currentRaceType = type
        val targetEnd = when (type) {
            RaceType.ACCELERATION_0_100 -> 100
            RaceType.ACCELERATION_0_200 -> 200
            RaceType.BRAKING_100_0 -> 0 // Target END es 0
            RaceType.CUSTOM -> 100 // Placeholder
        }
        val targetStart = if (type == RaceType.BRAKING_100_0) 100 else 0

        _session.value = RaceSession(
            type = type,
            targetSpeedStart = targetStart,
            targetSpeedEnd = targetEnd
        )
        
        _raceState.value = RaceState.ARMED
        startMonitoringLoop()
    }

    fun cancelRace() {
        stopMonitoringLoop()
        _raceState.value = RaceState.IDLE
        _session.value = null
    }

    private fun startMonitoringLoop() {
        raceJob?.cancel()
        raceJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                // 1. Obtener velocidad real (Alta frecuencia)
                val speedVal = requestSpeed()
                
                if (speedVal != null) {
                    val speed = speedVal.toInt()
                    _currentSpeed.value = speed
                    
                    // 2. Máquina de Estados
                    when (_raceState.value) {
                        RaceState.ARMED -> handleArmedState(speed)
                        RaceState.COUNTDOWN -> handleCountdownState() // Impl. simplificada
                        RaceState.RUNNING -> handleRunningState(speed)
                        else -> {}
                    }
                    
                    lastSpeed = speed
                    lastTime = SystemClock.elapsedRealtime()
                }

                delay(50) // 20Hz polling si el adaptador aguanta
            }
        }
    }

    private fun handleArmedState(speed: Int) {
        val session = _session.value ?: return

        if (session.type == RaceType.BRAKING_100_0) {
            // Para frenada, esperamos llegar a la velocidad objetivo de inicio (ej: 100)
            if (speed >= session.targetSpeedStart) {
                _raceState.value = RaceState.RUNNING
                startTimestamp = SystemClock.elapsedRealtime()
            }
        } else {
            // Para aceleración, esperamos pasar de 0 a >0
            if (lastSpeed == 0 && speed > 0) {
                // Auto-start sin countdown visual explícito aquí por simplicidad lógica
                _raceState.value = RaceState.RUNNING
                startTimestamp = SystemClock.elapsedRealtime()
            }
        }
    }

    private suspend fun handleCountdownState() {
        // Lógica opcional para semáforo de salida
        delay(3000)
        _raceState.value = RaceState.RUNNING
        startTimestamp = SystemClock.elapsedRealtime()
    }

    private fun handleRunningState(speed: Int) {
        val currentSession = _session.value ?: return
        val currentTime = SystemClock.elapsedRealtime()
        val elapsedSeconds = (currentTime - startTimestamp) / 1000f

        // A. Chequear finalización
        val finished = if (currentSession.type == RaceType.BRAKING_100_0) {
            speed <= currentSession.targetSpeedEnd
        } else {
            speed >= currentSession.targetSpeedEnd
        }

        if (finished) {
            finishRace(elapsedSeconds, speed)
            return
        }

        // B. Chequear aborto (frenada en aceleración)
        if (currentSession.type != RaceType.BRAKING_100_0 && speed < lastSpeed && speed > 5) {
            // Si baja velocidad significativamente (margen de ruido), error
            // _raceState.value = RaceState.ERROR
            // return
        }

        // C. splits (cada 10 km/h)
        // Lógica simplificada: si pasamos un múltiplo de 10 nuevo
        val lastFactor = lastSpeed / 10
        val currentFactor = speed / 10
        if (currentFactor > lastFactor) {
            val splitSpeed = currentFactor * 10
            val splitTime = (currentTime - startTimestamp)
            
            val newSplit = SplitTime(lastSpeed, splitSpeed, splitTime)
            _session.value = currentSession.copy(
                times = currentSession.times + newSplit
            )
        }

        // D. G-Force Calc
        val deltaV_ms = (speed - lastSpeed) * 0.27778f
        val deltaT_s = (currentTime - lastTime) / 1000f
        if (deltaT_s > 0) {
            val gForce = (deltaV_ms / deltaT_s) / 9.81f
            // Guardar max G
            if (abs(gForce) > currentSession.maxGforce) {
                _session.value = currentSession.copy(maxGforce = abs(gForce))
            }
        }
    }

    private fun finishRace(finalTimeSeconds: Float, finalSpeed: Int) {
        stopMonitoringLoop()
        _raceState.value = RaceState.FINISHED
        
        _session.value = _session.value?.copy(
            finalTime = finalTimeSeconds,
            completed = true
            // Podríamos ajustar el split final aquí
        )
    }

    private fun stopMonitoringLoop() {
        raceJob?.cancel()
    }

    private suspend fun requestSpeed(): Float? {
        if (!repository.isConnected()) return null
        return try {
            val command = protocol.buildCommand(ObdPid.SPEED)
            val response = repository.sendCommand(command)
            if (response.contains("NO DATA")) null else protocol.parseResponse(ObdPid.SPEED, response)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Progreso visual 0.0 - 1.0
     */
    fun getProgress(): Float {
        val s = _session.value ?: return 0f
        val current = _currentSpeed.value
        val range = abs(s.targetSpeedEnd - s.targetSpeedStart)
        if (range == 0) return 0f
        
        return if (s.type == RaceType.BRAKING_100_0) {
            // 100 -> 0. Progreso inv
            1f - ((current - s.targetSpeedEnd).toFloat() / range)
        } else {
            // 0 -> 100
             ((current - s.targetSpeedStart).toFloat() / range)
        }.coerceIn(0f, 1f)
    }
}
