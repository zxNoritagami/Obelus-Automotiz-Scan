package com.obelus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.obelus.data.local.entity.ScanSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Insert
    suspend fun insertSession(session: ScanSession): Long
    
    @Update
    suspend fun updateSession(session: ScanSession)
    
    @Query("SELECT * FROM scan_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ScanSession>>
    
    @Query("SELECT * FROM scan_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): ScanSession?
    
    @Query("DELETE FROM scan_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
    
    @Query("SELECT AVG(averageSpeed) FROM scan_sessions WHERE startTime > :since")
    suspend fun getAverageSpeedSince(since: Long): Float?
}
