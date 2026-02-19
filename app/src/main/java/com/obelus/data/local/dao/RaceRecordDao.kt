package com.obelus.data.local.dao

import androidx.room.*
import com.obelus.data.local.entity.RaceRecord

@Dao
interface RaceRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RaceRecord): Long

    @Update
    suspend fun update(record: RaceRecord)

    @Query("SELECT * FROM race_records ORDER BY startTime DESC")
    suspend fun getAll(): List<RaceRecord>

    @Query("SELECT * FROM race_records WHERE id = :id")
    suspend fun getById(id: Long): RaceRecord?

    @Query("SELECT * FROM race_records WHERE raceType = :type ORDER BY startTime DESC")
    suspend fun getByType(type: String): List<RaceRecord>

    /**
     * Returns the single best (lowest finalTimeSeconds) record for a given race type.
     * Used to compute delta vs personal best.
     */
    @Query("""
        SELECT * FROM race_records
        WHERE raceType = :type AND finalTimeSeconds > 0
        ORDER BY finalTimeSeconds ASC
        LIMIT 1
    """)
    suspend fun getBestByType(type: String): RaceRecord?

    /**
     * Returns the user's saved reference record (isReference = 1) for a race type.
     */
    @Query("SELECT * FROM race_records WHERE raceType = :type AND isReference = 1 LIMIT 1")
    suspend fun getReferenceByType(type: String): RaceRecord?

    @Query("UPDATE race_records SET isReference = 0 WHERE raceType = :type")
    suspend fun clearReferences(type: String)

    @Query("UPDATE race_records SET isReference = 1 WHERE id = :id")
    suspend fun markAsReference(id: Long)

    @Query("UPDATE race_records SET isPersonalBest = 0 WHERE raceType = :type")
    suspend fun clearPersonalBests(type: String)

    @Query("UPDATE race_records SET isPersonalBest = 1 WHERE id = :id")
    suspend fun markAsPersonalBest(id: Long)

    @Query("DELETE FROM race_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
