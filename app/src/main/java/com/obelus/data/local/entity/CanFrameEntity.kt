package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity para un frame CAN importado desde log.
 * Tabla: can_frames_log
 */
@Entity(
    tableName = "can_frames_log",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["canId"]),
        Index(value = ["timestamp"])
    ]
)
data class CanFrameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** ID de la sesión de importación (agrupador) */
    val sessionId: String,

    /** Timestamp en microsegundos desde el inicio del log */
    val timestamp: Long,

    /** CAN ID (11 o 29 bits) */
    val canId: Int,

    /** Payload en HEX (ej: "0A FF 12 00") — max 8 bytes */
    val dataHex: String,

    /** Bus index */
    val bus: Int = 0,

    /** True = 29-bit Extended Frame */
    val isExtended: Boolean = false,

    /** Nombre del archivo original de donde viene */
    val sourceFile: String = ""
)
