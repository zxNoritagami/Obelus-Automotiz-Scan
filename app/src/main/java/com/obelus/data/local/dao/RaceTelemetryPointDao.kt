package com.obelus.data.local.dao

import androidx.room.*
import com.obelus.data.local.entity.RaceTelemetryPoint

@Dao
interface RaceTelemetryPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: RaceTelemetryPoint): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<RaceTelemetryPoint>)

    /**
     * Returns all telemetry points for a race, ordered by time offset.
     * Each point is one ~100ms snapshot of speed, RPM, throttle, G-force.
     */
    @Query("SELECT * FROM race_telemetry WHERE raceId = :raceId ORDER BY timestampOffset ASC")
    suspend fun getByRace(raceId: Long): List<RaceTelemetryPoint>

    @Query("DELETE FROM race_telemetry WHERE raceId = :raceId")
    suspend fun deleteByRace(raceId: Long)

    @Query("SELECT COUNT(*) FROM race_telemetry WHERE raceId = :raceId")
    suspend fun countByRace(raceId: Long): Int
}
