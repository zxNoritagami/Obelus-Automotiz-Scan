package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "scan_sessions")
data class ScanSession(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long? = null,
    val vin: String? = null,
    val calibrationId: String? = null,
    val protocol: String,
    val elmDeviceName: String? = null,
    val totalFrames: Int = 0,
    val errorCount: Int = 0,
    val notes: String? = null
)
