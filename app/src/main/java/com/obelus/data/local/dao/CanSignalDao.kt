package com.obelus.data.local.dao

import androidx.room.*
import com.obelus.data.local.entity.CanSignal

@Dao
interface CanSignalDao {
    @Query("SELECT * FROM can_signals")
    suspend fun getAll(): List<CanSignal>

    @Query("SELECT * FROM can_signals WHERE id = :id")
    suspend fun getById(id: Long): CanSignal?

    @Query("SELECT * FROM can_signals WHERE sourceFile = :fileName")
    suspend fun getByFile(fileName: String): List<CanSignal>

    @Query("SELECT * FROM can_signals WHERE category = :category")
    suspend fun getByCategory(category: String): List<CanSignal>

    @Query("SELECT * FROM can_signals WHERE isFavorite = 1")
    suspend fun getFavorites(): List<CanSignal>

    @Query("SELECT * FROM can_signals WHERE name LIKE '%' || :query || '%'")
    suspend fun searchByName(query: String): List<CanSignal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(signal: CanSignal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(signals: List<CanSignal>)

    @Update
    suspend fun update(signal: CanSignal)

    @Delete
    suspend fun delete(signal: CanSignal)

    @Query("UPDATE can_signals SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    // ── Optimizadas con LIMIT (caché / precarga) ───────────────────────────────

    /**
     * Igual que [getByFile] pero con límite de resultados.
     * Se usa en precarga para no cargar DBCs enormes completos si no se necesitan.
     */
    @Query("SELECT * FROM can_signals WHERE sourceFile = :fileName ORDER BY name LIMIT :limit")
    suspend fun getByFileLimited(fileName: String, limit: Int = 200): List<CanSignal>

    /**
     * Búsqueda con límite para autocomplete en [AddSignalDialog].
     */
    @Query(
        "SELECT * FROM can_signals WHERE name LIKE '%' || :query || '%' " +
        "ORDER BY isFavorite DESC, name ASC LIMIT :limit"
    )
    suspend fun searchByNameLimited(query: String, limit: Int = 30): List<CanSignal>

    /**
     * Total de señales en BD (para decidir si vale la pena pre-cargar).
     */
    @Query("SELECT COUNT(*) FROM can_signals")
    suspend fun countAll(): Long
}
