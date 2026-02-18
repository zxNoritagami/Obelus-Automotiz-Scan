package com.obelus.data.local.model

import androidx.room.ColumnInfo

/**
 * Aggregated statistics for a signal.
 */
data class SignalStats(
    @ColumnInfo(name = "avg") val avg: Float,
    @ColumnInfo(name = "max") val max: Float,
    @ColumnInfo(name = "min") val min: Float
)
