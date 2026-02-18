package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val protocol: String = "AUTO",
    val notes: String = "",
    val averageSpeed: Float? = null,
    val maxRpm: Int? = null,
    val distanceKm: Float? = null,
    val isActive: Boolean = true
)
