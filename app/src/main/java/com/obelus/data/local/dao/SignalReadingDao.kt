package com.obelus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.obelus.data.local.entity.SignalReading
import com.obelus.data.local.model.SignalStats

@Dao
interface SignalReadingDao {
    @Insert
    suspend fun insert(reading: SignalReading): Long
    
    @Insert
    suspend fun insertAll(readings: List<SignalReading>)
    
    @Query("SELECT * FROM signal_readings WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun getBySession(sessionId: Long): List<SignalReading>

    @Query("SELECT * FROM signal_readings WHERE sessionId = :sessionId AND pid = :pid ORDER BY timestamp")
    suspend fun getByPid(sessionId: Long, pid: String): List<SignalReading>

    @Query("SELECT AVG(value) as avg, MAX(value) as max, MIN(value) as min FROM signal_readings WHERE pid = :pid AND timestamp > :since")
    suspend fun getStats(pid: String, since: Long): SignalStats

    // ── Optimizadas para gráficos y caché ─────────────────────────────────────

    /**
     * Ultimas [limit] lecturas de un PID ordenadas por timestamp DESC.
     * Ideal para sparklines y gráficos temporales sin cargar toda la sesión.
     */
    @Query(
        "SELECT * FROM signal_readings WHERE pid = :pid " +
        "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun getLatestReadingsFast(pid: String, limit: Int = 50): List<SignalReading>

    /**
     * Estadísticas de un PID acotadas en ventana temporal [since, until].
     */
    @Query(
        "SELECT AVG(value) as avg, MAX(value) as max, MIN(value) as min " +
        "FROM signal_readings " +
        "WHERE pid = :pid AND timestamp BETWEEN :since AND :until"
    )
    suspend fun getAggregatedStats(pid: String, since: Long, until: Long): SignalStats

    /**
     * Borra lecturas anteriores a [beforeTimestamp] para mantener la BD manejable.
     */
    @Query("DELETE FROM signal_readings WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldReadings(beforeTimestamp: Long)

    /**
     * Conteo total de lecturas de una sesión (rápido via COUNT).
     */
    @Query("SELECT COUNT(*) FROM signal_readings WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: Long): Long
}
