package com.obelus.data.cache

import android.content.Context
import android.util.LruCache
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.local.entity.SignalReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// CacheManager.kt
// Capa centralizada de caché en RAM (LruCache) y disco (DataStore).
// @Singleton inyectado por Hilt — sin bloquear el UI thread.
// ─────────────────────────────────────────────────────────────────────────────

private val Context.cacheDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "obelus_cache_prefs")

// ── Claves DataStore ──────────────────────────────────────────────────────────
private object CacheKeys {
    val LAST_BT_ADDRESS   = stringPreferencesKey("last_bt_address")
    val LAST_BT_NAME      = stringPreferencesKey("last_bt_name")
    val LAST_PROTOCOL     = stringPreferencesKey("last_protocol")
    val LAST_DBC_FILE     = stringPreferencesKey("last_dbc_file")
    val UNIT_SYSTEM       = stringPreferencesKey("unit_system")          // "metric"|"imperial"
    val DARK_MODE         = stringPreferencesKey("dark_mode")            // "dark"|"light"|"system"
    val DTC_LAST_READ_TS  = longPreferencesKey("dtc_last_read_ts")
    val CACHE_HIT_COUNT   = longPreferencesKey("cache_hit_count")
    val CACHE_MISS_COUNT  = longPreferencesKey("cache_miss_count")
}

// ── Tamaños de caché ──────────────────────────────────────────────────────────
private const val RAM_CACHE_MAX_BYTES  = 4 * 1024 * 1024   // 4 MB
private const val SIGNAL_LIST_MAX      = 512                // entradas en LRU
private const val READING_HISTORY_MAX  = 200                // lecturas por señal

/**
 * Gestiona el acceso a caché RAM y preferencias persistentes en disco.
 *
 * @param context Contexto de la aplicación (inyectado por Hilt).
 */
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── Contadores de métricas (no persistidas para rendimiento) ──────────────
    @Volatile private var hitCount  = 0L
    @Volatile private var missCount = 0L

    // ── LruCache principal ────────────────────────────────────────────────────
    private val ramCache: LruCache<String, Any> = object : LruCache<String, Any>(RAM_CACHE_MAX_BYTES) {
        override fun sizeOf(key: String, value: Any): Int = when (value) {
            is List<*>    -> (value.size * 256)              // ~256 bytes por objeto estimado
            is String     -> value.length * 2
            else          -> 128
        }.coerceAtMost(RAM_CACHE_MAX_BYTES / 4)             // ningún entry > 1 MB
    }

    // ── LruCache de lecturas históricas (rápido para gráficas) ───────────────
    private val readingHistoryCache: LruCache<String, ArrayDeque<SignalReading>> =
        LruCache(SIGNAL_LIST_MAX)

    private val dataStore = context.cacheDataStore

    // ═══════════════════════════════════════════════════════════════════════════
    // RAM cache – señales parseadas del DBC activo
    // ═══════════════════════════════════════════════════════════════════════════

    /** Guarda una lista de señales en RAM con la clave indicada. */
    fun putSignals(key: String, signals: List<CanSignal>) {
        ramCache.put(key, signals)
        println("[CacheManager] PUT signals → $key (${signals.size})")
    }

    /**
     * Recupera señales cacheadas. Actualiza contadores hit/miss.
     *
     * @return Lista de señales o null si no está en caché.
     */
    @Suppress("UNCHECKED_CAST")
    fun getSignals(key: String): List<CanSignal>? {
        val result = ramCache.get(key) as? List<CanSignal>
        if (result != null) hitCount++ else missCount++
        return result
    }

    /** Almacena cualquier objeto serializable en caché RAM. */
    fun put(key: String, value: Any) { ramCache.put(key, value) }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val v = ramCache.get(key)
        if (v != null) hitCount++ else missCount++
        @Suppress("UNCHECKED_CAST")
        return v as? T
    }

    /** Invalida una entrada o todo el caché RAM. */
    fun invalidate(key: String) { ramCache.remove(key); println("[CacheManager] INVALIDATE $key") }
    fun invalidateAll()         { ramCache.evictAll();  println("[CacheManager] INVALIDATE ALL") }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lecturas históricas rápidas (para gráficos sparkline)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Agrega una lectura al historial en-memoria de una señal.
     * Mantiene máximo [READING_HISTORY_MAX] entradas (más reciente al frente).
     */
    fun appendReading(pid: String, reading: SignalReading) {
        val deque = readingHistoryCache.get(pid) ?: ArrayDeque<SignalReading>().also {
            readingHistoryCache.put(pid, it)
        }
        deque.addFirst(reading)
        while (deque.size > READING_HISTORY_MAX) deque.removeLast()
    }

    /** Devuelve las últimas [count] lecturas de un PID (más reciente primero). */
    fun getRecentReadings(pid: String, count: Int = 50): List<SignalReading> =
        readingHistoryCache.get(pid)?.take(count) ?: emptyList()

    /** Limpia el historial de lecturas de una señal concreta. */
    fun clearReadingHistory(pid: String) { readingHistoryCache.remove(pid) }
    fun clearAllReadingHistory()         { readingHistoryCache.evictAll() }

    // ═══════════════════════════════════════════════════════════════════════════
    // DataStore – preferencias persistentes
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun saveLastBluetoothDevice(address: String, name: String) {
        dataStore.edit { prefs ->
            prefs[CacheKeys.LAST_BT_ADDRESS] = address
            prefs[CacheKeys.LAST_BT_NAME]    = name
        }
        println("[CacheManager] Saved last BT device → $name ($address)")
    }

    suspend fun getLastBluetoothDevice(): Pair<String, String>? {
        val prefs   = dataStore.data.first()
        val address = prefs[CacheKeys.LAST_BT_ADDRESS] ?: return null
        val name    = prefs[CacheKeys.LAST_BT_NAME]    ?: address
        return Pair(address, name)
    }

    suspend fun saveLastProtocol(protocolAtCommand: String) {
        dataStore.edit { it[CacheKeys.LAST_PROTOCOL] = protocolAtCommand }
    }
    suspend fun getLastProtocol(): String? = dataStore.data.first()[CacheKeys.LAST_PROTOCOL]

    suspend fun saveLastDbcFile(fileName: String) {
        dataStore.edit { it[CacheKeys.LAST_DBC_FILE] = fileName }
    }
    suspend fun getLastDbcFile(): String? = dataStore.data.first()[CacheKeys.LAST_DBC_FILE]

    suspend fun saveUnitSystem(system: String) {
        dataStore.edit { it[CacheKeys.UNIT_SYSTEM] = system }
    }
    suspend fun getUnitSystem(): String =
        dataStore.data.map { it[CacheKeys.UNIT_SYSTEM] ?: "metric" }.first()

    suspend fun saveDarkMode(mode: String) {
        dataStore.edit { it[CacheKeys.DARK_MODE] = mode }
    }
    suspend fun getDarkMode(): String =
        dataStore.data.map { it[CacheKeys.DARK_MODE] ?: "system" }.first()

    suspend fun saveDtcLastReadTimestamp(ts: Long) {
        dataStore.edit { it[CacheKeys.DTC_LAST_READ_TS] = ts }
    }
    suspend fun getDtcLastReadTimestamp(): Long =
        dataStore.data.map { it[CacheKeys.DTC_LAST_READ_TS] ?: 0L }.first()

    // ═══════════════════════════════════════════════════════════════════════════
    // Métricas
    // ═══════════════════════════════════════════════════════════════════════════

    /** Retorna hit rate como porcentaje (0-100). */
    fun hitRate(): Float {
        val total = hitCount + missCount
        return if (total == 0L) 0f else (hitCount * 100f / total)
    }

    fun printMetrics() {
        println("[CacheManager] Hits=$hitCount Misses=$missCount HitRate=${"%.1f".format(hitRate())}% | RAM used=${ramCache.size()}B / ${RAM_CACHE_MAX_BYTES}B")
    }
}
