package com.obelus.data.repository

import android.util.Log
import com.obelus.data.local.dao.ManufacturerDtcDao
import com.obelus.data.local.database.DtcImporter
import com.obelus.data.local.entity.ManufacturerDtcEntity
import com.obelus.data.model.DTC
import com.obelus.data.model.DTCCategory
import com.obelus.data.model.DTCSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado enriquecido de un DTC: combina datos OBD2 con info de fabricante.
 */
data class EnrichedDtc(
    val dtc: DTC,                           // DTC base del OBD2 scan
    val manufacturerInfo: ManufacturerDtcEntity?,  // Info específica de fabricante (o null)
    val manufacturer: String                // e.g. "VAG", "BMW", "TOYOTA", "GENERIC"
) {
    /** Descripción en español con fallback a inglés y luego a descripción OBD2 */
    val descriptionEs: String get() = when {
        manufacturerInfo?.descriptionEs?.isNotBlank() == true -> manufacturerInfo.descriptionEs
        manufacturerInfo?.descriptionEn?.isNotBlank() == true -> manufacturerInfo.descriptionEn
        else -> dtc.description
    }

    val systemLabel: String get() = manufacturerInfo?.system ?: "OTRO"

    val possibleCauses: List<String> get() = manufacturerInfo?.causes() ?: emptyList()

    val severityLevel: SeverityLevel get() = when(manufacturerInfo?.severity) {
        "ERROR"   -> SeverityLevel.ERROR
        "WARNING" -> SeverityLevel.WARNING
        "INFO"    -> SeverityLevel.INFO
        else       -> when (dtc.severity) {
            DTCSeverity.CRITICAL, DTCSeverity.HIGH -> SeverityLevel.ERROR
            DTCSeverity.MEDIUM                     -> SeverityLevel.WARNING
            DTCSeverity.LOW                        -> SeverityLevel.INFO
        }
    }
}

enum class SeverityLevel { ERROR, WARNING, INFO }

/**
 * Repository para DTCs enriquecidos con información de fabricante.
 *
 * Flujo de uso:
 * 1. Al iniciar la app: [ensureDatabaseSeeded] (idempotente)
 * 2. Al escanear DTCs: [enrichDtcs] recibe lista de DTC OBD2 + fabricante detectado
 * 3. La UI muestra EnrichedDtc con descripción ES, sistema afectado, causas y severidad
 *
 * Detección de fabricante: basada en prefijo del VIN (primer carácter indica región,
 * posiciones 2-3 indican fabricante). Fallback: detectar por patrón de código DTC.
 */
@Singleton
class ManufacturerDtcRepository @Inject constructor(
    private val dao: ManufacturerDtcDao,
    private val dtcImporter: DtcImporter
) {
    companion object {
        private const val TAG = "ManufacturerDtcRepo"

        // Prefijos WMI (World Manufacturer Identifier) del VIN - posiciones 1-3
        private val VIN_TO_MANUFACTURER = mapOf(
            // VAG
            "WVW" to "VAG", "WAU" to "VAG", "WBA" to "BMW", "WBS" to "BMW",
            "WBY" to "BMW", "WMW" to "BMW",  // Mini
            "JTD" to "TOYOTA", "JTM" to "TOYOTA", "SB1" to "TOYOTA",
            "JT3" to "TOYOTA", "JT2" to "TOYOTA", "1NX" to "TOYOTA", "4T1" to "TOYOTA",
            "VSS" to "VAG",  // Seat España
            "TBN" to "VAG",  // Škoda
        )
    }

    private var _detectedManufacturer: String = "GENERIC"
    val detectedManufacturer: String get() = _detectedManufacturer

    /**
     * Asegura que la base de datos de DTCs está inicializada.
     * Llamar al inicio de la app (Application.onCreate o desde HiltModule).
     */
    suspend fun ensureDatabaseSeeded() = dtcImporter.ensureSeeded()

    /**
     * Detecta el fabricante del vehículo a partir del VIN.
     * @param vin VIN completo o los primeros 3 caracteres (WMI)
     * @return "VAG", "BMW", "TOYOTA", o "GENERIC"
     */
    fun detectManufacturerFromVin(vin: String): String {
        if (vin.length < 3) return "GENERIC"
        val wmi = vin.take(3).uppercase()
        val manufacturer = VIN_TO_MANUFACTURER.entries
            .firstOrNull { wmi.startsWith(it.key) }?.value ?: "GENERIC"
        _detectedManufacturer = manufacturer
        Log.i(TAG, "VIN WMI=$wmi → Fabricante: $manufacturer")
        return manufacturer
    }

    /**
     * Enriquece una lista de DTCs OBD2 con información del fabricante.
     * Si el fabricante es GENERIC, busca en todas las tablas.
     *
     * @param dtcs Lista de DTCs del scan OBD2
     * @param manufacturer Fabricante detectado (VAG/BMW/TOYOTA/GENERIC)
     * @return Lista de EnrichedDtc ordenada por severidad (ERROR primero)
     */
    suspend fun enrichDtcs(
        dtcs: List<DTC>,
        manufacturer: String = _detectedManufacturer
    ): List<EnrichedDtc> = withContext(Dispatchers.IO) {

        if (dtcs.isEmpty()) return@withContext emptyList()

        val codes = dtcs.map { it.code }

        // Buscar info de fabricante para todos los códigos de una vez
        val manufacturerInfoMap: Map<String, ManufacturerDtcEntity> = when (manufacturer) {
            "GENERIC" -> dao.findByCodesAny(codes).associateBy { it.code }
            else      -> dao.findByCodesForManufacturer(codes, manufacturer).associateBy { it.code }
        }

        val enriched = dtcs.map { dtc ->
            val info = manufacturerInfoMap[dtc.code]
            EnrichedDtc(
                dtc             = dtc,
                manufacturerInfo = info,
                manufacturer    = manufacturer
            )
        }

        // Ordenar por severidad: ERROR → WARNING → INFO
        enriched.sortedWith(compareBy { it.severityLevel.ordinal })
    }

    /**
     * Busca info de un DTC individual por código.
     * Útil para la vista de detalle de un DTC.
     */
    suspend fun getDetailForCode(code: String, manufacturer: String = _detectedManufacturer): ManufacturerDtcEntity? =
        if (manufacturer == "GENERIC") dao.findByCode(code)
        else dao.findByCodeAndManufacturer(code, manufacturer) ?: dao.findByCode(code)

    /**
     * Obtiene DTCs agrupados por sistema para el fabricante activo.
     * Útil para filtrar por sistema en la UI.
     */
    suspend fun getDtcsBySystem(system: String): List<ManufacturerDtcEntity> =
        dao.getBySystem(_detectedManufacturer, system)

    /**
     * Establece manualmente el fabricante (útil si no hay VIN disponible).
     */
    fun setManufacturer(manufacturer: String) {
        _detectedManufacturer = manufacturer
        Log.i(TAG, "Fabricante establecido manualmente: $manufacturer")
    }
}
