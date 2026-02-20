package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.obelus.data.local.model.Endian
import com.obelus.data.local.model.SignalSource

/**
 * A single CAN signal definition imported from a .dbc file or created by the user.
 *
 * v6 changes (backward compatible — nullable fields with defaults):
 *   - [dbcDefinitionId]: optional FK to [DbcDefinition] for grouping signals
 *   - [isCustom]: true for signals created/edited directly in the app
 *   - [userNotes]: free-text notes added by the user
 *
 * Existing rows from DB v5 will have dbcDefinitionId=null, isCustom=false, userNotes=null
 * (handled by the Room v5→v6 migration that issues ALTER TABLE ADD COLUMN).
 */
@Entity(
    tableName = "can_signals",
    foreignKeys = [
        ForeignKey(
            entity = DbcDefinition::class,
            parentColumns = ["id"],
            childColumns = ["dbcDefinitionId"],
            onDelete = ForeignKey.SET_NULL   // Deleting a definition keeps its signals (unlinked)
        )
    ],
    indices = [
        Index(value = ["canId"]),
        Index(value = ["name"]),
        Index(value = ["category"]),
        Index(value = ["dbcDefinitionId"]),  // v6: allows fast "signals by definition" queries
        Index(value = ["isCustom"])          // v6: filter for user-created signals
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
    val createdAt: Long = System.currentTimeMillis(),

    // ── v6 fields ──────────────────────────────────────────────────────────

    /**
     * Optional FK to [DbcDefinition].
     * Null for legacy signals that existed before v6 or for signals not yet assigned.
     * ON DELETE SET_NULL: if the definition is deleted, the signal is kept but unlinked.
     */
    val dbcDefinitionId: Long? = null,

    /**
     * True when this signal was created or edited by the user inside the DBC editor.
     * False (default) for signals imported from .dbc files or shipped built-in.
     */
    val isCustom: Boolean = false,

    /**
     * Optional free-text notes the user can attach to a signal
     * (e.g., wiring color, sensor location, known quirks).
     */
    val userNotes: String? = null
)
