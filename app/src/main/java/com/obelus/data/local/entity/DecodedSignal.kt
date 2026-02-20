package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a decoded CAN signal value extracted from a log frame.
 *
 * Each row ties a raw CAN frame (via [frameId] + [sessionId]) to a [CanSignal]
 * definition, storing both the raw extracted bits and the engineering value
 * (rawBits * scale + offset).
 *
 * Created in batches by [com.obelus.data.canlog.LogRepository.applyDbcDefinition].
 */
@Entity(
    tableName = "decoded_signals",
    foreignKeys = [
        ForeignKey(
            entity = CanSignal::class,
            parentColumns = ["id"],
            childColumns = ["signalId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DbcDefinition::class,
            parentColumns = ["id"],
            childColumns = ["dbcDefinitionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["signalId"]),
        Index(value = ["dbcDefinitionId"]),
        Index(value = ["sessionId", "dbcDefinitionId"]),
        Index(value = ["signalId", "sessionId"])
    ]
)
data class DecodedSignal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Row-id of the source CanFrameEntity (for joining if needed). */
    val frameId: Long,

    /** Session the source frame belongs to. */
    val sessionId: String,

    /** FK → can_signals.id */
    val signalId: Long,

    /** Cached signal name (avoids extra join in list queries). */
    val signalName: String,

    /** CAN ID of the source frame (hex, e.g. "7E0"). */
    val canId: String,

    /** Timestamp of the source frame in microseconds. */
    val timestamp: Long,

    /** Raw integer bits extracted from the frame payload (before scale/offset). */
    val rawBits: Long,

    /** Engineering value: rawBits * scale + offset */
    val calculatedValue: Float,

    /** Unit string from the signal definition (e.g. "km/h", "°C"). */
    val unit: String?,

    /** FK → dbc_definitions.id */
    val dbcDefinitionId: Long
)
