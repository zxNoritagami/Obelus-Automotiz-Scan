package com.obelus.obelusscan.domain.model

import com.obelus.data.local.entity.RaceTelemetryPoint
import com.obelus.domain.race.RaceAnalysisResult
import java.util.UUID

/**
 * Represents a time interval for a speed bracket (e.g., 0-10, 10-20 km/h).
 */
data class SplitTime(
    val speedFrom: Int,
    val speedTo: Int,
    val timeMs: Long
)

/**
 * Supported race / performance test types.
 */
enum class RaceType {
    ACCELERATION_0_100,
    ACCELERATION_0_200,
    BRAKING_100_0,
    CUSTOM
}

/**
 * State machine states of the race.
 */
enum class RaceState {
    IDLE, ARMED, COUNTDOWN, RUNNING, FINISHED, ERROR
}

/**
 * In-memory immutable model for a race session.
 * [telemetryPoints] accumulates during RUNNING and drives [RaceAnalyzer]
 * for post-race analysis before persisting to Room as a RaceRecord.
 */
data class RaceSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val type: RaceType,
    val targetSpeedStart: Int,
    val targetSpeedEnd: Int,

    // Live results
    val times: List<SplitTime> = emptyList(),
    val finalTime: Float = 0f,
    val maxGforce: Float = 0f,
    val completed: Boolean = false,

    // Telemetry captured every ~100ms during RUNNING
    val telemetryPoints: List<RaceTelemetryPoint> = emptyList(),

    // OBD data at boundaries
    val coolantTempStart: Int = -1,
    val coolantTempEnd: Int = -1,

    // Post-race analysis (populated after finishRace())
    val analysisResult: RaceAnalysisResult? = null,

    // Room FK after persistence (0 = not yet persisted)
    val persistedId: Long = 0L
) {
    init {
        if (type != RaceType.BRAKING_100_0 && targetSpeedEnd <= targetSpeedStart) {
            throw IllegalArgumentException("targetSpeedEnd ($targetSpeedEnd) must be > start ($targetSpeedStart) for acceleration.")
        }
        if (type == RaceType.BRAKING_100_0 && targetSpeedEnd >= targetSpeedStart) {
            throw IllegalArgumentException("targetSpeedEnd ($targetSpeedEnd) must be < start ($targetSpeedStart) for braking.")
        }
    }
}
