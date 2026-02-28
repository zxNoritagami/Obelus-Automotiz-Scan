package com.obelus.obelusscan.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mechanicDataStore by preferencesDataStore(name = "mechanic_prefs")

@Singleton
class MechanicDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val mechanicNameFlow: Flow<String> = context.mechanicDataStore.data
        .map { it[MECHANIC_NAME_KEY] ?: "" }
        .catch { emit("") } // NO CRASHEAR por corrupción
    
    suspend fun setMechanicName(name: String) {
        context.mechanicDataStore.edit { it[MECHANIC_NAME_KEY] = name.take(10) }
    }
    
    suspend fun getMechanicName(): String {
        return mechanicNameFlow.first()
    }
    
    companion object {
        private val MECHANIC_NAME_KEY = stringPreferencesKey("mechanic_name")
    }
}
