package com.obelus.data.local.dao

import androidx.room.*
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.local.entity.DbcDefinition
import com.obelus.data.local.entity.DbcSignalOverride

// ─────────────────────────────────────────────────────────────────────────────
// Relation model — DbcDefinition with its signals and overrides
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full DBC profile: definition + all linked signals + all overrides.
 * Used for the editor screen and for exporting to .dbc format.
 */
data class DbcDefinitionWithSignals(
    @Embedded
    val definition: DbcDefinition,

    @Relation(
        parentColumn = "id",
        entityColumn = "dbcDefinitionId"
    )
    val signals: List<CanSignal>,

    @Relation(
        parentColumn = "id",
        entityColumn = "dbcDefinitionId"
    )
    val overrides: List<DbcSignalOverride>
)

// ─────────────────────────────────────────────────────────────────────────────
// DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface DbcDefinitionDao {

    // ── DbcDefinition CRUD ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDefinition(definition: DbcDefinition): Long

    @Update
    suspend fun updateDefinition(definition: DbcDefinition)

    @Query("DELETE FROM dbc_definitions WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteUserDefinition(id: Long)

    /** All definitions, newest first */
    @Query("SELECT * FROM dbc_definitions ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DbcDefinition>

    /** Only user-created profiles (not shipped built-ins) */
    @Query("SELECT * FROM dbc_definitions WHERE isBuiltIn = 0 ORDER BY updatedAt DESC")
    suspend fun getUserDefinitions(): List<DbcDefinition>

    /** Only built-in, read-only profiles */
    @Query("SELECT * FROM dbc_definitions WHERE isBuiltIn = 1 ORDER BY name ASC")
    suspend fun getBuiltInDefinitions(): List<DbcDefinition>

    @Query("SELECT * FROM dbc_definitions WHERE id = :id")
    suspend fun getById(id: Long): DbcDefinition?

    @Query("SELECT * FROM dbc_definitions WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): DbcDefinition?

    // ── Full profile (with nested signals + overrides) ────────────────────

    @Transaction
    @Query("SELECT * FROM dbc_definitions WHERE id = :id")
    suspend fun getWithSignals(id: Long): DbcDefinitionWithSignals?

    @Transaction
    @Query("SELECT * FROM dbc_definitions ORDER BY updatedAt DESC")
    suspend fun getAllWithSignals(): List<DbcDefinitionWithSignals>

    // ── Atomic definition + signals insert ───────────────────────────────

    /**
     * Inserts a complete DBC profile (definition + its signals) in a single
     * atomic transaction. Returns the generated definition ID.
     *
     * The [signals] list is expected to have [CanSignal.dbcDefinitionId] == 0;
     * this method fills in the real FK after inserting the definition row.
     * Signals are inserted with IGNORE conflict strategy so re-importing the
     * same named signal won't duplicate it.
     */
    @Transaction
    suspend fun insertDefinitionWithSignals(
        definition: DbcDefinition,
        signals: List<CanSignal>
    ): Long {
        val defId = insertDefinition(definition)
        val linked = signals.map { it.copy(dbcDefinitionId = defId) }
        insertSignals(linked)
        // Update signal count cache
        updateSignalCount(defId, linked.size)
        return defId
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSignals(signals: List<CanSignal>)

    @Query("UPDATE dbc_definitions SET signalCount = :count, updatedAt = :now WHERE id = :id")
    suspend fun updateSignalCount(
        id: Long,
        count: Int,
        now: Long = System.currentTimeMillis()
    )

    // ── DbcSignalOverride CRUD ────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: DbcSignalOverride): Long

    @Delete
    suspend fun deleteOverride(override: DbcSignalOverride)

    @Query("SELECT * FROM dbc_signal_overrides WHERE dbcDefinitionId = :defId AND canSignalId = :signalId LIMIT 1")
    suspend fun getOverride(defId: Long, signalId: Long): DbcSignalOverride?

    @Query("SELECT * FROM dbc_signal_overrides WHERE dbcDefinitionId = :defId")
    suspend fun getOverridesForDefinition(defId: Long): List<DbcSignalOverride>

    @Query("DELETE FROM dbc_signal_overrides WHERE dbcDefinitionId = :defId")
    suspend fun clearOverrides(defId: Long)

    // ── Signal search within a definition ────────────────────────────────

    @Query("""
        SELECT * FROM can_signals
        WHERE dbcDefinitionId = :defId
        ORDER BY name ASC
    """)
    suspend fun getSignalsForDefinition(defId: Long): List<CanSignal>

    @Query("""
        SELECT * FROM can_signals
        WHERE dbcDefinitionId = :defId
          AND (name LIKE '%' || :query || '%' OR canId LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    suspend fun searchSignals(defId: Long, query: String): List<CanSignal>

    /** Returns all user-created signals (isCustom = 1), not tied to any built-in file */
    @Query("SELECT * FROM can_signals WHERE isCustom = 1 ORDER BY createdAt DESC")
    suspend fun getCustomSignals(): List<CanSignal>
}
