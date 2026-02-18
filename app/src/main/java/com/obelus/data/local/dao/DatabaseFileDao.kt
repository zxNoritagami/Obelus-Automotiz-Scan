package com.obelus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.obelus.data.local.entity.DatabaseFile

@Dao
interface DatabaseFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: DatabaseFile)

    @Query("SELECT * FROM database_files")
    suspend fun getAll(): List<DatabaseFile>

    @Query("SELECT * FROM database_files WHERE isActive = 1")
    suspend fun getActive(): List<DatabaseFile>

    @Query("SELECT * FROM database_files WHERE fileName = :fileName")
    suspend fun getByName(fileName: String): DatabaseFile?

    @Query("UPDATE database_files SET isActive = :active WHERE fileName = :fileName")
    suspend fun setActive(fileName: String, active: Boolean)

    @Query("DELETE FROM database_files WHERE fileName = :fileName")
    suspend fun delete(fileName: String)
}
