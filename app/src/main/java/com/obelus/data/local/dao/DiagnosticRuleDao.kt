package com.obelus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.obelus.data.local.entity.DiagnosticRule

/**
 * Acceso a datos para las reglas de diagnóstico persistidas en Room.
 */
@Dao
interface DiagnosticRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: DiagnosticRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<DiagnosticRule>)

    @Query("SELECT * FROM diagnostic_rules WHERE dtcCode = :dtcCode")
    suspend fun getRulesByDtc(dtcCode: String): List<DiagnosticRule>

    @Query("SELECT * FROM diagnostic_rules")
    suspend fun getAllRules(): List<DiagnosticRule>

    @Query("DELETE FROM diagnostic_rules")
    suspend fun deleteAll()
}
