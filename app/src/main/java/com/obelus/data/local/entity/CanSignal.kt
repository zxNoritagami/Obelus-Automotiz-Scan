package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.obelus.data.local.model.Endian
import com.obelus.data.local.model.SignalSource

@Entity(
    tableName = "can_signals",
    indices = [
        Index(value = ["canId"]),
        Index(value = ["name"]),
        Index(value = ["category"])
    ]
)
data class CanSignal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String?,
    val canId: String,
    val isExtended: Boolean,
    val startByte: Int,
    val startBit: Int,
    val bitLength: Int,
    val endianness: Endian,
    val scale: Float,
    val offset: Float,
    val signed: Boolean,
    val unit: String?,
    val minValue: Float?,
    val maxValue: Float?,
    val source: SignalSource,
    val sourceFile: String?,
    val category: String?,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
