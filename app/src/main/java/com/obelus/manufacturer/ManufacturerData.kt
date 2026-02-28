package com.obelus.manufacturer

// ─────────────────────────────────────────────────────────────────────────────
// ManufacturerData.kt
// Definición de un dato específico de fabricante (Modo 21/22).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fabricantes soportados en la base de datos estática.
 * WMI y nombres para referencia en UI.
 */
enum class Manufacturer(val displayName: String, val wmiPrefixes: List<String>) {
    TOYOTA   ("Toyota",   listOf("JT", "JD", "JA", "JF")),
    HONDA    ("Honda",    listOf("JH", "JHM", "2HG")),
    FORD     ("Ford",     listOf("1F", "2F", "3F", "WF0")),
    GM       ("GM",       listOf("1G", "2G", "KL", "W0L", "1GC")),
    VW       ("VW/Audi",  listOf("WV", "WA", "WAU", "VWV")),
    BMW      ("BMW",      listOf("WB", "WBA", "WBY", "4US")),
    MERCEDES ("Mercedes", listOf("WD", "WDB", "WDD")),
    HYUNDAI  ("Hyundai",  listOf("KM", "KMH")),
    KIA      ("Kia",      listOf("KNA", "KNAfA")),
    NISSAN   ("Nissan",   listOf("JN", "JN1", "1N4")),
    MAZDA    ("Mazda",    listOf("JM", "JM1")),
    SUBARU   ("Subaru",   listOf("JF1", "JF2", "4S3")),
    GENERIC  ("Genérico", emptyList())
}

/**
 * Dato específico de fabricante leído vía Modo 21 o 22.
 *
 * @param dataId          Identificador único global (ej. "TOYOTA_OIL_PRESSURE").
 * @param manufacturer    Fabricante al que aplica.
 * @param model           Modelo específico (null = todos los modelos de ese fabricante).
 * @param years           Rango de años de aplicación (null = universal).
 * @param description     Descripción legible de qué mide el dato.
 * @param mode            Modo OBD2: "21" o "22".
 * @param pid             Dirección PID hex (ej. "01F4" para modo 22).
 * @param requestBytes    Bytes adicionales de la solicitud, si aplica (null = solo mode+pid).
 * @param responseParser  Cómo interpretar los bytes de respuesta.
 * @param scale           Factor de escala aplicado al valor crudo.
 * @param offset          Offset sumado tras aplicar la escala.
 * @param unit            Unidad de medida (ej. "kPa", "°C", "rpm").
 * @param normalMin       Límite inferior del rango normal (null = no aplica).
 * @param normalMax       Límite superior del rango normal (null = no aplica).
 * @param encodingTable   Para [ResponseType.ENCODED]: mapa de índice→descripción.
 */
data class ManufacturerData(
    val dataId: String,
    val manufacturer: Manufacturer,
    val model: String?           = null,
    val years: IntRange?         = null,
    val description: String,
    val mode: String,            // "21" | "22"
    val pid: String,             // hex, e.g. "01F4"
    val requestBytes: String?    = null,
    val responseParser: ResponseType,
    val scale: Float             = 1.0f,
    val offset: Float            = 0.0f,
    val unit: String             = "",
    val normalMin: Float?        = null,
    val normalMax: Float?        = null,
    val encodingTable: Map<Int, String> = emptyMap()
) {
    /** Columna de bytes a enviar al ECU (mode + pid + requestBytes). */
    fun buildRequest(): String {
        val req = "${mode}${pid}"
        return if (requestBytes != null) "$req $requestBytes" else req
    }
}

// ── Resultado de lectura ──────────────────────────────────────────────────────

/** Resultado de una lectura de dato específico de fabricante. */
sealed class ManufacturerReading {

    /** Lectura exitosa. */
    data class Value(
        val data: ManufacturerData,
        val raw: ByteArray,
        val decoded: Float,
        val displayString: String,     // con unidad y formato
        val isNormal: Boolean,
        val timestampMs: Long = System.currentTimeMillis()
    ) : ManufacturerReading()

    /** El vehículo no responde a este PID / no lo soporta. */
    data class NotSupported(
        val data: ManufacturerData,
        val reason: String = "Sin respuesta del ECU a PID ${data.pid}"
    ) : ManufacturerReading()

    /** Error de comunicación durante la lectura. */
    data class Error(
        val data: ManufacturerData,
        val message: String
    ) : ManufacturerReading()
}

/** Información decodificada de un VIN. */
data class VinInfo(
    val vin: String,
    val wmi: String,                 // World Manufacturer Identifier (primeros 3 chars)
    val manufacturer: Manufacturer,
    val modelYear: Int?,             // puede deducirse del dígito 10
    val assemblyCountry: String
)
