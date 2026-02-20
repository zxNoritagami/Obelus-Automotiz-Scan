package com.obelus.data.local.dao

import androidx.room.*
import com.obelus.data.local.entity.DecodedSignal
import kotlinx.coroutines.flow.Flow

@Dao
interface DecodedSignalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(signals: List<DecodedSignal>)

    /**
     * All decoded signals for a session + DBC definition combo, ordered by timestamp.
     * Reactive — emits whenever rows are inserted or deleted.
     */
    @Query("""
        SELECT * FROM decoded_signals
        WHERE sessionId = :sessionId AND dbcDefinitionId = :dbcDefinitionId
        ORDER BY timestamp ASC
    """)
    fun getForSession(sessionId: String, dbcDefinitionId: Long): Flow<List<DecodedSignal>>

    /**
     * All values for a single signal in a session, ordered by timestamp.
     * Used for the sparkline / detail chart.
     */
    @Query("""
        SELECT * FROM decoded_signals
        WHERE signalId = :signalId AND sessionId = :sessionId
        ORDER BY timestamp ASC
    """)
    suspend fun getForSignal(signalId: Long, sessionId: String): List<DecodedSignal>

    /**
     * Latest value of each distinct signal in a session+DBC combo.
     * Efficient for building the summary card list (one card per signal).
     */
    @Query("""
        SELECT * FROM decoded_signals
        WHERE sessionId = :sessionId AND dbcDefinitionId = :dbcDefinitionId
        GROUP BY signalId
        HAVING timestamp = MAX(timestamp)
        ORDER BY signalName ASC
    """)
    fun getLatestPerSignal(sessionId: String, dbcDefinitionId: Long): Flow<List<DecodedSignal>>

    /** Count of decoded rows for a session + DBC combo. */
    @Query("""
        SELECT COUNT(*) FROM decoded_signals
        WHERE sessionId = :sessionId AND dbcDefinitionId = :dbcDefinitionId
    """)
    suspend fun countForSession(sessionId: String, dbcDefinitionId: Long): Int

    /** Remove all decoded results for a session + DBC (to re-decode). */
    @Query("""
        DELETE FROM decoded_signals
        WHERE sessionId = :sessionId AND dbcDefinitionId = :dbcDefinitionId
    """)
    suspend fun deleteForSession(sessionId: String, dbcDefinitionId: Long)

    /** Remove everything tied to a session (when session is cleared). */
    @Query("DELETE FROM decoded_signals WHERE sessionId = :sessionId")
    suspend fun deleteAllForSession(sessionId: String)
}
