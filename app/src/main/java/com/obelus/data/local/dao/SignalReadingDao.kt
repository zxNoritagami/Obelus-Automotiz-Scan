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
}
