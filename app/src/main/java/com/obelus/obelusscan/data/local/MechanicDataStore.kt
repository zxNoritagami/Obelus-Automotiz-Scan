package com.obelus.obelusscan.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mechanicDataStore: DataStore<Preferences> by preferencesDataStore(name = "mechanic_prefs")

@Singleton
class MechanicDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val MECHANIC_NAME = stringPreferencesKey("mechanic_name")
        val CURRENT_VALID_HASH = stringPreferencesKey("current_valid_hash")
        val HASH_TIMESTAMP = longPreferencesKey("hash_timestamp")
        const val HASH_EXPIRY_MINUTES = 60
    }

    val mechanicNameFlow: Flow<String> = context.mechanicDataStore.data.map { prefs ->
        prefs[MECHANIC_NAME] ?: "Mecanico"
    }

    suspend fun setMechanicName(name: String) {
        context.mechanicDataStore.edit { prefs ->
            prefs[MECHANIC_NAME] = name
        }
    }

    suspend fun getMechanicName(): String {
        return context.mechanicDataStore.data.first()[MECHANIC_NAME] ?: "Mecanico"
    }

    suspend fun setCurrentHash(hash: String, timestamp: Long) {
        context.mechanicDataStore.edit { prefs ->
            prefs[CURRENT_VALID_HASH] = hash
            prefs[HASH_TIMESTAMP] = timestamp
        }
    }

    suspend fun getCurrentHash(): String? {
        return context.mechanicDataStore.data.first()[CURRENT_VALID_HASH]
    }

    suspend fun getHashTimestamp(): Long {
        return context.mechanicDataStore.data.first()[HASH_TIMESTAMP] ?: 0L
    }

    suspend fun clearHash() {
        context.mechanicDataStore.edit { prefs ->
            prefs.remove(CURRENT_VALID_HASH)
            prefs.remove(HASH_TIMESTAMP)
        }
    }
}
