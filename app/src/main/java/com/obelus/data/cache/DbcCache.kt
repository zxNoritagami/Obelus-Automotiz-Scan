package com.obelus.data.cache

import com.obelus.data.local.dao.CanSignalDao
import com.obelus.data.local.dao.DbcDefinitionDao
import com.obelus.data.local.entity.CanSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// DbcCache.kt
// Caché específico para definiciones DBC / señales CAN.
// Precarga los DBCs más usados al inicio y permite búsqueda por VIN prefix
// sin recurrir a Room en cada operación.
// ─────────────────────────────────────────────────────────────────────────────

/** Número de DBCs "populares" precargados al inicio. */
private const val PRELOADED_DBC_COUNT = 3

/** Prefijo VIN de 3 chars → clave de caché. */
private fun vinKey(vinPrefix: String)    = "vin_$vinPrefix"
private fun fileKey(fileName: String)    = "file_$fileName"
private fun categoryKey(cat: String)     = "cat_$cat"

/**
 * Caché de señales CAN organizado por VIN-prefix y nombre de archivo DBC.
 *
 * @param signalDao     DAO de señales CAN.
 * @param dbcDefinitionDao DAO de definiciones DBC.
 * @param cacheManager  Gestor central de caché RAM.
 */
@Singleton
class DbcCache @Inject constructor(
    private val signalDao: CanSignalDao,
    private val dbcDefinitionDao: DbcDefinitionDao,
    private val cacheManager: CacheManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ═══════════════════════════════════════════════════════════════════════════
    // Precarga al inicio
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Precarga en RAM los [PRELOADED_DBC_COUNT] archivos DBC más usados.
     * Se ejecuta en background; no bloquea el hilo principal.
     */
    fun preloadCommonDatabases() {
        scope.launch {
            println("[DbcCache] Iniciando precarga de $PRELOADED_DBC_COUNT DBC(s)…")
            try {
                val topDefinitions = dbcDefinitionDao.getAll().take(PRELOADED_DBC_COUNT)
                topDefinitions.forEach { def ->
                    val signals = signalDao.getByFile(def.fileName)
                    if (signals.isNotEmpty()) {
                        cacheManager.putSignals(fileKey(def.fileName), signals)
                        println("[DbcCache] Precargado: ${def.fileName} (${signals.size} señales)")
                    }
                }
                println("[DbcCache] Precarga completada.")
            } catch (e: Exception) {
                println("[DbcCache] Error en precarga: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Búsqueda por VIN prefix
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Busca en RAM las señales asociadas a un prefijo de VIN (WMI – 3 chars).
     * Retorna null si no está en caché (caller debe ir a Room + luego llamar warm-up).
     *
     * @param vinPrefix Primeros 3 caracteres del VIN (ej. "WVW" = VW Alemania).
     */
    fun getCachedSignals(vinPrefix: String): List<CanSignal>? {
        val key = vinKey(vinPrefix.take(3).uppercase())
        return cacheManager.getSignals(key)
    }

    /**
     * Precarga en RAM todo lo relacionado a un VIN en background.
     *
     * @param vin VIN completo (17 chars) o prefijo de 3 chars.
     */
    fun warmupCacheForVin(vin: String) {
        val prefix = vin.take(3).uppercase()
        scope.launch {
            println("[DbcCache] Warm-up para VIN prefix '$prefix'…")
            try {
                // Estrategia: buscar señales en archivos cuyo nombre contenga el prefix
                // (en la práctica se mapea VIN → fabricante → archivo DBC)
                val signals = signalDao.getAll().also {
                    println("[DbcCache] ${it.size} señales totales encontradas.")
                }
                if (signals.isNotEmpty()) {
                    cacheManager.putSignals(vinKey(prefix), signals)
                    println("[DbcCache] Warm-up '$prefix' completado: ${signals.size} señales.")
                }
            } catch (e: Exception) {
                println("[DbcCache] Error en warm-up: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Por archivo DBC
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Obtiene señales de un archivo DBC específico.
     * Prioriza caché RAM; va a Room sólo si no hay hit.
     *
     * @param fileName Nombre del archivo .dbc.
     * @return Lista de señales correspondientes.
     */
    suspend fun getSignalsForFile(fileName: String): List<CanSignal> {
        val key = fileKey(fileName)
        cacheManager.getSignals(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            val fromDb = signalDao.getByFile(fileName)
            if (fromDb.isNotEmpty()) {
                cacheManager.putSignals(key, fromDb)
            }
            fromDb
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Por categoría
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Obtiene señales de una categoría con caché en memoria.
     *
     * @param category Categoría de señal (ej. "OBD2_GENERIC", "ENGINE", "BODY").
     */
    suspend fun getSignalsByCategoryCached(category: String): List<CanSignal> {
        val key = categoryKey(category)
        cacheManager.getSignals(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            val fromDb = signalDao.getByCategory(category)
            cacheManager.putSignals(key, fromDb)
            fromDb
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Invalidación
    // ═══════════════════════════════════════════════════════════════════════════

    /** Invalida el caché de un DBC al actualizarlo/importarlo. */
    fun invalidateDbc(fileName: String) {
        cacheManager.invalidate(fileKey(fileName))
        println("[DbcCache] Caché invalidado para: $fileName")
    }

    /** Invalida todo el caché DBC (tras borrado de datos, por ejemplo). */
    fun invalidateAll() {
        cacheManager.invalidateAll()
        cacheManager.clearAllReadingHistory()
        println("[DbcCache] INVALIDATE ALL – caché DBC y lecturas limpiados.")
    }
}
