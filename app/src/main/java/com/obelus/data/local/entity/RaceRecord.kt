package com.obelus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists the final result of a completed race session in Room.
 * Telemetry points are stored separately in [RaceTelemetryPoint] with FK to [id].
 *
 * [splitsJson] stores the serialized list of SplitTime as a simple
 * pipe-delimited string: "0|10|1234|10|20|890|..." so we avoid a Gson/Moshi dep.
 * Format per split: speedFrom|speedTo|timeMs
 */
@Entity(
    tableName = "race_records",
    indices = [
        Index(value = ["raceType"]),
        Index(value = ["startTime"]), // Optimizacion PROMPT 13: Indice por tiempo para historial mas rapido
        Index(value = ["isReference"])
    ]
)
data class RaceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** ISO string: ACCELERATION_0_100 | ACCELERATION_0_200 | BRAKING_100_0 | CUSTOM */
    val raceType: String,

    /** Epoch millis of race start */
    val startTime: Long = System.currentTimeMillis(),

    /** Total time from start speed to target speed, in seconds */
    val finalTimeSeconds: Float,

    /** Target speed range */
    val targetSpeedStart: Int,
    val targetSpeedEnd: Int,

    /** Maximum G-force recorded during the run */
    val maxGForce: Float,

    /** Estimated peak power in HP — 0 if vehicle weight not set */
    val estimatedHp: Float,

    /** Reaction time in ms: delay between throttle 100% and first movement */
    val reactionTimeMs: Long,

    /** Coolant temperature at start and end of race (°C), -1 if not available */
    val coolantTempStart: Int = -1,
    val coolantTempEnd: Int = -1,

    /** Pipe-delimited splits: "speedFrom|speedTo|timeMs|..." */
    val splitsJson: String = "",

    /** True if this is the user's reference session for comparisons */
    val isReference: Boolean = false,

    /** True if this session is the personal best for its [raceType] */
    val isPersonalBest: Boolean = false
)
