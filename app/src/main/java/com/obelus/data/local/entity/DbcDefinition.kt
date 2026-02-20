package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a named DBC (Database CAN) definition set.
 * A DbcDefinition groups one or more [CanSignal] entries under a logical file/profile.
 *
 * Built-in profiles (shipped with the app from .dbc resources) have [isBuiltIn] = true
 * and cannot be deleted by the user. User-created profiles have [isBuiltIn] = false.
 *
 * Relationship:
 *   DbcDefinition 1 ──────────────── N CanSignal  (via CanSignal.dbcDefinitionId)
 *   DbcDefinition 1 ──────────────── N DbcSignalOverride
 */
@Entity(
    tableName = "dbc_definitions",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["isBuiltIn"]),
        Index(value = ["updatedAt"])
    ]
)
data class DbcDefinition(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Human-readable profile name, e.g., "BMW E46 CAN-B" */
    val name: String,

    /** Optional longer description shown in the editor */
    val description: String? = null,

    /** Epoch millis when this definition was first created */
    val createdAt: Long = System.currentTimeMillis(),

    /** Epoch millis of last modification (update on any signal change) */
    val updatedAt: Long = System.currentTimeMillis(),

    /** True if shipped with the app (read-only); false if user-created or imported */
    val isBuiltIn: Boolean = false,

    /** File origin — null for user-created, filename for imported .dbc files */
    val sourceFile: String? = null,

    /** Protocol hint: "CAN", "CANFD", "OBD2", "UDS" — informational only */
    val protocol: String = "CAN",

    /** Number of signals cached here so the list UI doesn't need to JOIN */
    val signalCount: Int = 0
)
