package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_access_logs")
data class SecurityAccessLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val ecuName: String,
    val brand: String,
    val algorithm: String,
    val isSuccess: Boolean,
    val vin: String,
    val details: String? = null
)
