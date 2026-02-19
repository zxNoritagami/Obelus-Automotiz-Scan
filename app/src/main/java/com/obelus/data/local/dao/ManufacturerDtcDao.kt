package com.obelus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.obelus.data.local.entity.ManufacturerDtcEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManufacturerDtcDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(dtcs: List<ManufacturerDtcEntity>)

    /** Busca un DTC por código para cualquier fabricante */
    @Query("SELECT * FROM manufacturer_dtcs WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): ManufacturerDtcEntity?

    /** Busca un DTC por código y fabricante específico */
    @Query("SELECT * FROM manufacturer_dtcs WHERE code = :code AND manufacturer = :manufacturer LIMIT 1")
    suspend fun findByCodeAndManufacturer(code: String, manufacturer: String): ManufacturerDtcEntity?

    /** Todos los DTCs de un fabricante, ordenados por código */
    @Query("SELECT * FROM manufacturer_dtcs WHERE manufacturer = :manufacturer ORDER BY code ASC")
    fun getAllForManufacturer(manufacturer: String): Flow<List<ManufacturerDtcEntity>>

    /** Busca por código para MÚLTIPLES fabricantes (ej: enriquecimiento de DTCs OBD2) */
    @Query("SELECT * FROM manufacturer_dtcs WHERE code IN (:codes)")
    suspend fun findByCodesAny(codes: List<String>): List<ManufacturerDtcEntity>

    /** Para un conjunto de códigos + fabricante específico */
    @Query("SELECT * FROM manufacturer_dtcs WHERE code IN (:codes) AND manufacturer = :manufacturer")
    suspend fun findByCodesForManufacturer(codes: List<String>, manufacturer: String): List<ManufacturerDtcEntity>

    /** Conteo para saber si ya están inicializados los datos */
    @Query("SELECT COUNT(*) FROM manufacturer_dtcs WHERE manufacturer = :manufacturer AND isSeeded = 1")
    suspend fun countSeeded(manufacturer: String): Int

    /** DTCs por sistema (ENGINE, ABS, etc.) */
    @Query("SELECT * FROM manufacturer_dtcs WHERE manufacturer = :manufacturer AND system = :system ORDER BY severity ASC")
    suspend fun getBySystem(manufacturer: String, system: String): List<ManufacturerDtcEntity>

    @Query("DELETE FROM manufacturer_dtcs WHERE manufacturer = :manufacturer")
    suspend fun deleteByManufacturer(manufacturer: String)
}
