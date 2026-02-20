package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-specific override for a single signal inside a [DbcDefinition].
 *
 * Used when the user wants to adjust a built-in signal (e.g., rename it,
 * change its display unit, set custom min/max alerts) without modifying
 * the original [CanSignal] row — preserving the upstream definition intact.
 *
 * Relationships:
 *   DbcSignalOverride N ── 1 DbcDefinition  (CASCADE delete)
 *   DbcSignalOverride N ── 1 CanSignal       (CASCADE delete)
 *
 * Usage pattern:
 *   When rendering a signal, the UI checks if a DbcSignalOverride exists
 *   for the (dbcDefinitionId, canSignalId) pair and merges non-null fields
 *   on top of the base CanSignal.
 */
@Entity(
    tableName = "dbc_signal_overrides",
    foreignKeys = [
        ForeignKey(
            entity = DbcDefinition::class,
            parentColumns = ["id"],
            childColumns = ["dbcDefinitionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CanSignal::class,
            parentColumns = ["id"],
            childColumns = ["canSignalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["dbcDefinitionId"]),
        Index(value = ["canSignalId"]),
        Index(value = ["dbcDefinitionId", "canSignalId"], unique = true)
    ]
)
data class DbcSignalOverride(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** FK → DbcDefinition.id */
    val dbcDefinitionId: Long,

    /** FK → CanSignal.id */
    val canSignalId: Long,

    // ── Overridable fields (null = use original CanSignal value) ──────────

    /** Display alias for this signal within this definition */
    val nameOverride: String? = null,

    /** Overridden unit label (e.g., "°F" instead of "°C") */
    val unitOverride: String? = null,

    /** Alert threshold — signal value above this triggers a warning */
    val alertMaxValue: Float? = null,

    /** Alert threshold — signal value below this triggers a warning */
    val alertMinValue: Float? = null,

    /** User notes / wiring info for this specific signal */
    val notes: String? = null,

    /** Whether to show this signal in the live dashboard */
    val showInDashboard: Boolean = false,

    /** Epoch millis of last modification */
    val updatedAt: Long = System.currentTimeMillis()
)
