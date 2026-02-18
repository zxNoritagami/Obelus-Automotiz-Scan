package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dtc_codes",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["category"])
    ]
)
data class DtcCode(
    @PrimaryKey
    val code: String,
    val description: String?,
    val category: Char,
    val isActive: Boolean,
    val isPending: Boolean,
    val isPermanent: Boolean,
    val firstSeen: Long?,
    val lastSeen: Long?,
    val clearedAt: Long?,
    val sessionId: String?
)
