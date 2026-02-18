package com.obelus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.obelus.data.local.entity.SignalReading

@Dao
interface SignalReadingDao {
    @Insert
    suspend fun insertReading(reading: SignalReading)
    
    @Insert
    suspend fun insertReadings(readings: List<SignalReading>)
    
    @Query("SELECT * FROM signal_readings WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun getReadingsForSession(sessionId: Long): List<SignalReading>
    
    @Query("SELECT * FROM signal_readings WHERE sessionId = :sessionId AND pid = :pid ORDER BY timestamp")
    suspend fun getReadingsForPid(sessionId: Long, pid: String): List<SignalReading>
    
    @Query("DELETE FROM signal_readings WHERE sessionId = :sessionId")
    suspend fun deleteReadingsForSession(sessionId: Long)
    
    // Statistics
    @Query("SELECT AVG(value) FROM signal_readings WHERE sessionId = :sessionId AND pid = :pid")
    suspend fun getAverageForPid(sessionId: Long, pid: String): Float?
    
    @Query("SELECT MAX(value) FROM signal_readings WHERE sessionId = :sessionId AND pid = :pid")
    suspend fun getMaxForPid(sessionId: Long, pid: String): Float?
}
