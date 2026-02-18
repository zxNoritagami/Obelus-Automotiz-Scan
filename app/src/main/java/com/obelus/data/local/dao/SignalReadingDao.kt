package com.obelus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.obelus.data.local.entity.SignalReading
import com.obelus.data.local.model.SignalStats

@Dao
interface SignalReadingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: SignalReading): Long

    @Query("SELECT * FROM signal_readings WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: String): List<SignalReading>

    @Query("SELECT * FROM signal_readings WHERE signalId = :signalId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getBySignal(signalId: Long, limit: Int): List<SignalReading>

    @Query("SELECT * FROM signal_readings WHERE signalId = :signalId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBySignal(signalId: Long): SignalReading?

    @Query("SELECT AVG(decodedValue) as avg, MAX(decodedValue) as max, MIN(decodedValue) as min FROM signal_readings WHERE signalId = :signalId AND timestamp >= :since")
    suspend fun getStats(signalId: Long, since: Long): SignalStats

    @Query("DELETE FROM signal_readings WHERE timestamp < :cutoff")
    suspend fun deleteOld(cutoff: Long)

    @Query("DELETE FROM signal_readings WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
