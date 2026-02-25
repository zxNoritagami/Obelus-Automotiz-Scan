package com.obelus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One telemetry snapshot captured every ~100ms during an active race.
 * Foreign key to [RaceRecord] with CASCADE delete so removing a race
 * automatically removes all its telemetry.
 */
@Entity(
    tableName = "race_telemetry",
    foreignKeys = [
        ForeignKey(
            entity = RaceRecord::class,
            parentColumns = ["id"],
            childColumns = ["raceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["raceId", "timestampOffset"]) // Optimizacion PROMPT 13: Indice compuesto
    ]
)
data class RaceTelemetryPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** FK to RaceRecord.id */
    val raceId: Long,

    /** Milliseconds elapsed since race start (not wall-clock) */
    val timestampOffset: Long,

    /** Vehicle speed in km/h at this moment */
    val speedKmh: Int,

    /** Engine RPM — 0 if not available */
    val rpm: Int,

    /** Throttle position 0–100 % — -1 if not available */
    val throttlePct: Int,

    /** Instantaneous G-force (acceleration axis) */
    val gForce: Float,

    /** Coolant temperature °C — -1 if not available */
    val coolantTemp: Int
)
