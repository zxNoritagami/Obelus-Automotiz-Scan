package com.obelus.data.local.dao

import androidx.room.*
import com.obelus.data.local.entity.ScanSession

@Dao
interface ScanSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ScanSession)
    
    @Update
    suspend fun update(session: ScanSession)
    
    @Query("SELECT * FROM scan_sessions ORDER BY startTime DESC")
    suspend fun getAll(): List<ScanSession>
    
    @Query("SELECT * FROM scan_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: Long): ScanSession?
    
    @Query("SELECT * FROM scan_sessions WHERE endTime IS NULL OR endTime = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActive(): ScanSession?

    @Query("DELETE FROM scan_sessions WHERE id = :sessionId")
    suspend fun delete(sessionId: Long)
}
