package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "signal_readings",
    foreignKeys = [
        ForeignKey(
            entity = CanSignal::class,
            parentColumns = ["id"],
            childColumns = ["signalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["signalId"]),
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"])
    ]
)
data class SignalReading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val signalId: Long,
    val rawBytes: String,
    val decodedValue: Float,
    val timestamp: Long,
    val sessionId: String,
    val vehicleState: String?
)
