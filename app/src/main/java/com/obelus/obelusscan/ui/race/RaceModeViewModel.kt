package com.obelus.obelusscan.ui.race

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.local.dao.RaceRecordDao
import com.obelus.data.local.dao.RaceTelemetryPointDao
import com.obelus.data.local.entity.RaceRecord
import com.obelus.data.local.entity.RaceTelemetryPoint
import com.obelus.data.repository.ObdRepository
import com.obelus.domain.model.ObdPid
import com.obelus.domain.race.RaceAnalysisResult
import com.obelus.domain.race.RaceAnalyzer
import com.obelus.obelusscan.data.local.SettingsDataStore
import com.obelus.obelusscan.data.protocol.ObdProtocol
import com.obelus.obelusscan.domain.model.RaceSession
import com.obelus.obelusscan.domain.model.RaceState
import com.obelus.obelusscan.domain.model.RaceType
import com.obelus.obelusscan.domain.model.SplitTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class RaceModeViewModel @Inject constructor(
    private val repository: ObdRepository,
    private val raceRecordDao: RaceRecordDao,
    private val raceTelemetryPointDao: RaceTelemetryPointDao,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val protocol = ObdProtocol()
    private var raceJob: Job? = null

    // ── UI states ────────────────────────────────────────────────────────
    private val _raceState  = MutableStateFlow(RaceState.IDLE)
    val raceState: StateFlow<RaceState> = _raceState.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0)
    val currentSpeed: StateFlow<Int> = _currentSpeed.asStateFlow()

    private val _session = MutableStateFlow<RaceSession?>(null)
    val session: StateFlow<RaceSession?> = _session.asStateFlow()

    private val _analysisResult = MutableStateFlow<RaceAnalysisResult?>(null)
    val analysisResult: StateFlow<RaceAnalysisResult?> = _analysisResult.asStateFlow()

    /** True when the current finished session has been marked as reference */
    private val _savedAsReference = MutableStateFlow(false)
    val savedAsReference: StateFlow<Boolean> = _savedAsReference.asStateFlow()

    // ── Internal state ───────────────────────────────────────────────────
    private var startTimestamp: Long = 0
    private var lastSpeed: Int       = 0
    private var lastTime: Long       = 0
    private var currentRaceType      = RaceType.ACCELERATION_0_100

    /** In-memory telemetry buffer (one entry per ~100ms) */
    private val telemetryBuffer = mutableListOf<RaceTelemetryPoint>()

    // ── Vehicle weight (from Settings) ───────────────────────────────────
    val vehicleWeightKg: StateFlow<Int> = settingsDataStore.vehicleWeightKg
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1200)

    // ────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────

    fun armRace(type: RaceType) {
        currentRaceType = type
        val targetEnd = when (type) {
            RaceType.ACCELERATION_0_100 -> 100
            RaceType.ACCELERATION_0_200 -> 200
            RaceType.BRAKING_100_0      -> 0
            RaceType.CUSTOM             -> 100
        }
        val targetStart = if (type == RaceType.BRAKING_100_0) 100 else 0

        _session.value = RaceSession(type = type, targetSpeedStart = targetStart, targetSpeedEnd = targetEnd)
        _analysisResult.value = null
        _savedAsReference.value = false
        telemetryBuffer.clear()

        _raceState.value = RaceState.ARMED
        startMonitoringLoop()
    }

    fun cancelRace() {
        stopMonitoringLoop()
        _raceState.value = RaceState.IDLE
        _session.value   = null
        telemetryBuffer.clear()
    }

    /**
     * Marks the just-finished session as the user's reference for future comparisons.
     * Clears previous reference for the same race type.
     */
    fun saveAsReference() {
        val id = _session.value?.persistedId ?: return
        if (id == 0L) return
        val type = currentRaceType.name
        viewModelScope.launch(Dispatchers.IO) {
            raceRecordDao.clearReferences(type)
            raceRecordDao.markAsReference(id)
            _savedAsReference.value = true
        }
    }

    // Visual progress 0.0–1.0
    fun getProgress(): Float {
        val s = _session.value ?: return 0f
        val current = _currentSpeed.value
        val range = abs(s.targetSpeedEnd - s.targetSpeedStart)
        if (range == 0) return 0f
        return if (s.type == RaceType.BRAKING_100_0) {
            1f - ((current - s.targetSpeedEnd).toFloat() / range)
        } else {
            (current - s.targetSpeedStart).toFloat() / range
        }.coerceIn(0f, 1f)
    }

    // ────────────────────────────────────────────────────────────────────
    // Monitoring loop
    // ────────────────────────────────────────────────────────────────────

    private fun startMonitoringLoop() {
        raceJob?.cancel()
        raceJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val speedVal = requestSpeed()
                if (speedVal != null) {
                    val speed = speedVal.toInt()
                    _currentSpeed.value = speed

                    when (_raceState.value) {
                        RaceState.ARMED     -> handleArmedState(speed)
                        RaceState.COUNTDOWN -> handleCountdownState()
                        RaceState.RUNNING   -> handleRunningState(speed)
                        else -> {}
                    }

                    // Capture telemetry when running
                    if (_raceState.value == RaceState.RUNNING) {
                        captureTelemetryPoint(speed)
                    }

                    lastSpeed = speed
                    lastTime  = SystemClock.elapsedRealtime()
                }
                delay(100) // 10 Hz — balances accuracy vs ELM327 latency
            }
        }
    }

    private fun handleArmedState(speed: Int) {
        val s = _session.value ?: return
        if (s.type == RaceType.BRAKING_100_0) {
            if (speed >= s.targetSpeedStart) {
                _raceState.value = RaceState.RUNNING
                startTimestamp = SystemClock.elapsedRealtime()
            }
        } else {
            if (lastSpeed == 0 && speed > 0) {
                _raceState.value = RaceState.RUNNING
                startTimestamp = SystemClock.elapsedRealtime()
            }
        }
    }

    private suspend fun handleCountdownState() {
        delay(3000)
        _raceState.value = RaceState.RUNNING
        startTimestamp = SystemClock.elapsedRealtime()
    }

    private fun handleRunningState(speed: Int) {
        val currentSession = _session.value ?: return
        val currentTime    = SystemClock.elapsedRealtime()
        val elapsedSeconds = (currentTime - startTimestamp) / 1000f

        val finished = if (currentSession.type == RaceType.BRAKING_100_0) {
            speed <= currentSession.targetSpeedEnd
        } else {
            speed >= currentSession.targetSpeedEnd
        }

        if (finished) {
            finishRace(elapsedSeconds, speed)
            return
        }

        // Splits every 10 km/h
        val lastFactor    = lastSpeed / 10
        val currentFactor = speed / 10
        if (currentFactor != lastFactor) {
            val splitSpeed = currentFactor * 10
            val splitTime  = currentTime - startTimestamp
            val newSplit   = SplitTime(lastSpeed, splitSpeed, splitTime)
            _session.value = currentSession.copy(times = currentSession.times + newSplit)
        }

        // G-force (per-call, max tracked on session)
        val deltaV = (speed - lastSpeed) * 0.27778f
        val deltaT = (currentTime - lastTime) / 1000f
        if (deltaT > 0) {
            val g = abs(deltaV / deltaT) / 9.81f
            if (g > currentSession.maxGforce) {
                _session.value = currentSession.copy(maxGforce = g)
            }
        }
    }

    private fun captureTelemetryPoint(speed: Int) {
        val offset = SystemClock.elapsedRealtime() - startTimestamp
        telemetryBuffer.add(
            RaceTelemetryPoint(
                raceId          = 0L,  // filled in after Room insert
                timestampOffset = offset,
                speedKmh        = speed,
                rpm             = 0,   // would require extra OBD PID request
                throttlePct     = -1,  // would require PID 0x11
                gForce          = _session.value?.maxGforce ?: 0f,
                coolantTemp     = -1
            )
        )
    }

    private fun finishRace(finalTimeSeconds: Float, finalSpeed: Int) {
        stopMonitoringLoop()
        _raceState.value = RaceState.FINISHED

        val finishedSession = _session.value?.copy(
            finalTime = finalTimeSeconds,
            completed = true,
            telemetryPoints = telemetryBuffer.toList()
        ) ?: return

        _session.value = finishedSession

        // Post-race analysis + Room persistence
        viewModelScope.launch(Dispatchers.IO) {
            val weight   = vehicleWeightKg.value
            val analysis = RaceAnalyzer.analyze(
                telemetry      = finishedSession.telemetryPoints,
                finalTimeS     = finalTimeSeconds,
                finalSpeedKmh  = finalSpeed,
                vehicleWeightKg = weight
            )
            _analysisResult.value = analysis

            // Persist to Room
            val splitsStr = finishedSession.times.joinToString("|") {
                "${it.speedFrom}|${it.speedTo}|${it.timeMs}"
            }
            val record = RaceRecord(
                raceType         = finishedSession.type.name,
                startTime        = finishedSession.startTime,
                finalTimeSeconds = finalTimeSeconds,
                targetSpeedStart = finishedSession.targetSpeedStart,
                targetSpeedEnd   = finishedSession.targetSpeedEnd,
                maxGForce        = analysis.maxGForce,
                estimatedHp      = analysis.estimatedHp,
                reactionTimeMs   = analysis.reactionTimeMs,
                splitsJson       = splitsStr
            )
            val recordId = raceRecordDao.insert(record)

            // Mark as personal best if it's the fastest for this type
            val best = raceRecordDao.getBestByType(finishedSession.type.name)
            if (best == null || finalTimeSeconds <= best.finalTimeSeconds) {
                raceRecordDao.clearPersonalBests(finishedSession.type.name)
                raceRecordDao.markAsPersonalBest(recordId)
            }

            // Persist telemetry points
            val telemetryEntities = finishedSession.telemetryPoints.map { it.copy(raceId = recordId) }
            raceTelemetryPointDao.insertAll(telemetryEntities)

            // Update session with persistedId
            _session.value = finishedSession.copy(persistedId = recordId, analysisResult = analysis)
        }
    }

    private fun stopMonitoringLoop() {
        raceJob?.cancel()
    }

    private suspend fun requestSpeed(): Float? {
        if (!repository.isConnected()) return null
        return try {
            val command  = protocol.buildCommand(ObdPid.SPEED)
            val response = repository.sendCommand(command)
            if (response.contains("NO DATA")) null
            else protocol.parseResponse(ObdPid.SPEED, response)
        } catch (e: Exception) { null }
    }
}
