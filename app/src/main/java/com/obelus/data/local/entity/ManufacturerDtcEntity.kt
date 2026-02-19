package com.obelus.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Entidad única para DTCs específicos de fabricante.
 * Cubre VAG (VW/Audi/Seat/Skoda), BMW y Toyota/Lexus.
 *
 * Estrategia de diseño: una sola tabla polimórfica en lugar de 3 tablas separadas,
 * filtrada por [manufacturer]. Evita duplicar DAOs/Repos y facilita búsquedas cruzadas.
 */
@Entity(
    tableName = "manufacturer_dtcs",
    indices = [
        Index(value = ["code"]),
        Index(value = ["manufacturer"]),
        Index(value = ["code", "manufacturer"], unique = true)
    ]
)
data class ManufacturerDtcEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Código OBD2 estándar o específico del fabricante (ej: "P0011", "18010") */
    val code: String,

    /** Fabricante: "VAG", "BMW", "TOYOTA" */
    val manufacturer: String,

    /** Descripción en español */
    val descriptionEs: String,

    /** Descripción en inglés (fallback) */
    val descriptionEn: String,

    /**
     * Severidad: "ERROR", "WARNING", "INFO"
     * ERROR   = falla crítica (falla motor, airbag, ABS)
     * WARNING = degradación de rendimiento
     * INFO    = informativo / monitoreo
     */
    val severity: String,

    /**
     * Sistema afectado: ENGINE, TRANSMISSION, ABS, AIRBAG,
     * FUEL, EMISSIONS, ELECTRICAL, BODY, NETWORK, OTHER
     */
    val system: String,

    /** Posibles causas separadas por '|' (se deserializa con TypeConverter) */
    val possibleCauses: String,  // "Sensor sucio|Cable roto|Válvula atascada"

    /** True si fue inicializado desde JSON (no importado de sesión OBD2) */
    val isSeeded: Boolean = true
) {
    /** Convierte la cadena de causas en lista */
    fun causes(): List<String> = possibleCauses.split("|").filter { it.isNotBlank() }
}

/** Enum helper para severidad — usado solo en UI/lógica, no en DB */
enum class DtcSeverity { ERROR, WARNING, INFO }

/** Enum helper para sistema */
enum class DtcSystem {
    ENGINE, TRANSMISSION, ABS, AIRBAG, FUEL, EMISSIONS, ELECTRICAL, BODY, NETWORK, OTHER;

    fun labelEs(): String = when (this) {
        ENGINE       -> "Motor"
        TRANSMISSION -> "Transmisión"
        ABS          -> "ABS / Frenos"
        AIRBAG       -> "Airbag / SRS"
        FUEL         -> "Sistema de combustible"
        EMISSIONS    -> "Emisiones"
        ELECTRICAL   -> "Eléctrico"
        BODY         -> "Carrocería"
        NETWORK      -> "Red CAN / Bus"
        OTHER        -> "Otro"
    }
}
