package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.obelus.data.local.model.FileType

@Entity(tableName = "database_files")
data class DatabaseFile(
    @PrimaryKey
    val fileName: String,
    val fileType: FileType,
    val filePath: String,
    val vehicleMake: String?,
    val vehicleModel: String?,
    val vehicleYear: Int?,
    val signalCount: Int,
    val importedAt: Long,
    val isActive: Boolean
)
