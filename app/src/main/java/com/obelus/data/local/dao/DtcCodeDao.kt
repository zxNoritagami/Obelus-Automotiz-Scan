package com.obelus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.obelus.data.local.entity.DtcCode

@Dao
interface DtcCodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dtc: DtcCode)

    @Update
    suspend fun update(dtc: DtcCode)

    @Query("SELECT * FROM dtc_codes WHERE isActive = 1")
    suspend fun getAllActive(): List<DtcCode>

    @Query("SELECT * FROM dtc_codes WHERE code = :code")
    suspend fun getByCode(code: String): DtcCode?

    @Query("SELECT * FROM dtc_codes WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: String): List<DtcCode>

    @Query("UPDATE dtc_codes SET isActive = 0, clearedAt = :timestamp WHERE code = :code")
    suspend fun clearDtc(code: String, timestamp: Long)

    @Query("DELETE FROM dtc_codes")
    suspend fun deleteAll()
}
