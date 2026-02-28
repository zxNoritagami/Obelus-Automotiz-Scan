package com.obelus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.obelus.data.local.entity.SecurityAccessLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityAccessDao {
    @Insert
    suspend fun insertLog(log: SecurityAccessLog)

    @Query("SELECT * FROM security_access_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SecurityAccessLog>>

    @Query("SELECT * FROM security_access_logs WHERE vin = :vin ORDER BY timestamp DESC")
    fun getLogsByVin(vin: String): Flow<List<SecurityAccessLog>>
}
