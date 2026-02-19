package com.obelus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.obelus.data.local.entity.CanFrameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CanFrameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(frames: List<CanFrameEntity>)

    @Query("SELECT * FROM can_frames_log WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getFramesForSession(sessionId: String): Flow<List<CanFrameEntity>>

    /** Consulta paginada para LazyColumn de alto rendimiento */
    @Query("""
        SELECT * FROM can_frames_log 
        WHERE sessionId = :sessionId 
          AND canId BETWEEN :minId AND :maxId
        ORDER BY timestamp ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFramesPaged(
        sessionId: String,
        minId: Int,
        maxId: Int,
        limit: Int,
        offset: Int
    ): List<CanFrameEntity>

    @Query("SELECT COUNT(*) FROM can_frames_log WHERE sessionId = :sessionId")
    suspend fun countFrames(sessionId: String): Int

    @Query("SELECT DISTINCT sessionId FROM can_frames_log")
    suspend fun getAllSessionIds(): List<String>

    @Query("DELETE FROM can_frames_log WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM can_frames_log")
    suspend fun deleteAll()
}
