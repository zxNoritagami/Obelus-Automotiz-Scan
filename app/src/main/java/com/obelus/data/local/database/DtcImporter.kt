package com.obelus.data.local.database

import android.content.Context
import android.util.Log
import com.obelus.data.local.dao.ManufacturerDtcDao
import com.obelus.data.local.entity.ManufacturerDtcEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Importador inicial de DTCs de fabricante desde archivos JSON en res/raw/.
 *
 * Solo ejecuta la inserción si la tabla está vacía para ese fabricante,
 * garantizando que el seed ocurra exactamente una vez al primer arranque.
 *
 * Se invoca desde [ManufacturerDtcRepository.ensureDatabaseSeeded()].
 */
@Singleton
class DtcImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ManufacturerDtcDao
) {
    companion object {
        private const val TAG = "DtcImporter"

        // Mapa: fabricante → resource raw ID
        // Los IDs se resuelven en runtime usando getIdentifier()
        private val MANUFACTURERS = mapOf(
            "VAG"    to "vag_dtcs",
            "BMW"    to "bmw_dtcs",
            "TOYOTA" to "toyota_dtcs"
        )
    }

    /**
     * Verifica si cada fabricante ya tiene datos y hace el seed si no.
     * Idempotente — seguro de llamar múltiples veces.
     */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        for ((manufacturer, resourceName) in MANUFACTURERS) {
            val count = dao.countSeeded(manufacturer)
            if (count > 0) {
                Log.d(TAG, "$manufacturer ya inicializado ($count códigos)")
                continue
            }

            Log.i(TAG, "Inicializando DTCs de $manufacturer desde $resourceName.json...")
            importFromRaw(manufacturer, resourceName)
        }
    }

    /**
     * Parsea el JSON del resource y lo inserta en Room.
     *
     * Formato JSON esperado (array de objetos):
     * ```json
     * [
     *   {
     *     "code": "P0011",
     *     "descriptionEs": "...",
     *     "descriptionEn": "...",
     *     "severity": "ERROR",
     *     "system": "ENGINE",
     *     "possibleCauses": "Causa 1|Causa 2|Causa 3"
     *   }
     * ]
     * ```
     */
    private suspend fun importFromRaw(manufacturer: String, resourceName: String) {
        try {
            // Resolver ID del resource en runtime
            val resId = context.resources.getIdentifier(
                resourceName, "raw", context.packageName
            )
            if (resId == 0) {
                Log.e(TAG, "Recurso no encontrado: $resourceName.json")
                return
            }

            val jsonText = context.resources.openRawResource(resId)
                .bufferedReader()
                .use { it.readText() }

            val jsonArray = JSONArray(jsonText)
            val entities = mutableListOf<ManufacturerDtcEntity>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                entities.add(
                    ManufacturerDtcEntity(
                        code           = obj.getString("code"),
                        manufacturer   = manufacturer,
                        descriptionEs  = obj.getString("descriptionEs"),
                        descriptionEn  = obj.optString("descriptionEn", ""),
                        severity       = obj.optString("severity", "WARNING"),
                        system         = obj.optString("system", "OTHER"),
                        possibleCauses = obj.optString("possibleCauses", ""),
                        isSeeded       = true
                    )
                )
            }

            // Insertar en batches para seguridad
            entities.chunked(50).forEach { batch ->
                dao.insertAll(batch)
            }

            Log.i(TAG, "✅ $manufacturer: ${entities.size} DTCs importados")

        } catch (e: Exception) {
            Log.e(TAG, "Error importando $manufacturer DTCs: ${e.message}", e)
        }
    }

    /**
     * Re-importa todos los DTCs (útil para actualizaciones de datos).
     * Borra primero los datos seeded existentes.
     */
    suspend fun forceReimport(manufacturer: String? = null) = withContext(Dispatchers.IO) {
        val targets = if (manufacturer != null)
            mapOf(manufacturer to (MANUFACTURERS[manufacturer] ?: return@withContext))
        else MANUFACTURERS

        for ((mfr, resName) in targets) {
            dao.deleteByManufacturer(mfr)
            importFromRaw(mfr, resName)
        }
    }
}
