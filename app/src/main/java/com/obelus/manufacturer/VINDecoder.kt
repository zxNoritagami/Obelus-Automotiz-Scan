package com.obelus.manufacturer

import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// VINDecoder.kt
// Decodificador básico de VIN (Vehicle Identification Number).
// No requiere internet – 100% datos estáticos.
// ─────────────────────────────────────────────────────────────────────────────

/** Tabla de año de modelo (posición 10 del VIN, J-2800 1980+). */
private val MODEL_YEAR_TABLE = mapOf(
    'A' to 1980, 'B' to 1981, 'C' to 1982, 'D' to 1983, 'E' to 1984,
    'F' to 1985, 'G' to 1986, 'H' to 1987, 'J' to 1988, 'K' to 1989,
    'L' to 1990, 'M' to 1991, 'N' to 1992, 'P' to 1993, 'R' to 1994,
    'S' to 1995, 'T' to 1996, 'V' to 1997, 'W' to 1998, 'X' to 1999,
    'Y' to 2000, '1' to 2001, '2' to 2002, '3' to 2003, '4' to 2004,
    '5' to 2005, '6' to 2006, '7' to 2007, '8' to 2008, '9' to 2009,
    'A' to 2010, 'B' to 2011, 'C' to 2012, 'D' to 2013, 'E' to 2014,
    'F' to 2015, 'G' to 2016, 'H' to 2017, 'J' to 2018, 'K' to 2019,
    'L' to 2020, 'M' to 2021, 'N' to 2022, 'P' to 2023, 'R' to 2024,
    'S' to 2025, 'T' to 2026
)

/**
 * WMI → Fabricante (los más comunes a nivel global + LATAM).
 * Ordenados de más específicos (3 chars) a más genéricos (2 chars)
 * para búsqueda correcta por prefijo.
 */
private val WMI_MAP: List<Pair<String, Pair<Manufacturer, String>>> = listOf(
    // Toyota (Japón, EEUU, México)
    "JTD" to (Manufacturer.TOYOTA to "Japón"),
    "JT2" to (Manufacturer.TOYOTA to "Japón"),
    "JT3" to (Manufacturer.TOYOTA to "Japón"),
    "JT4" to (Manufacturer.TOYOTA to "Japón"),
    "JTM" to (Manufacturer.TOYOTA to "Japón/México"),
    "JT"  to (Manufacturer.TOYOTA to "Japón"),
    // Honda
    "JHM" to (Manufacturer.HONDA to "Japón"),
    "2HG" to (Manufacturer.HONDA to "Canadá"),
    "19X" to (Manufacturer.HONDA to "EE.UU."),
    "5FN" to (Manufacturer.HONDA to "EE.UU."),
    "JH"  to (Manufacturer.HONDA to "Japón"),
    // Ford
    "WF0" to (Manufacturer.FORD to "Alemania"),
    "6FP" to (Manufacturer.FORD to "Australia"),
    "3FA" to (Manufacturer.FORD to "México"),
    "1FA" to (Manufacturer.FORD to "EE.UU."),
    "1FB" to (Manufacturer.FORD to "EE.UU."),
    "1FC" to (Manufacturer.FORD to "EE.UU."),
    "1FD" to (Manufacturer.FORD to "EE.UU."),
    "1FT" to (Manufacturer.FORD to "EE.UU."),
    "3F"  to (Manufacturer.FORD to "México"),
    "1F"  to (Manufacturer.FORD to "EE.UU."),
    "2F"  to (Manufacturer.FORD to "Canadá"),
    // GM
    "1GC" to (Manufacturer.GM to "EE.UU."),
    "1GT" to (Manufacturer.GM to "EE.UU."),
    "2G1" to (Manufacturer.GM to "Canadá"),
    "3G1" to (Manufacturer.GM to "México"),
    "W0L" to (Manufacturer.GM to "Alemania"),
    "KL"  to (Manufacturer.GM to "Corea"),
    "1G"  to (Manufacturer.GM to "EE.UU."),
    "2G"  to (Manufacturer.GM to "Canadá"),
    // VW/Audi
    "WAU" to (Manufacturer.VW to "Alemania (Audi)"),
    "WVW" to (Manufacturer.VW to "Alemania (VW)"),
    "WV1" to (Manufacturer.VW to "Alemania (VW)"),
    "WV2" to (Manufacturer.VW to "Alemania (VW)"),
    "9BW" to (Manufacturer.VW to "Brasil"),
    "8AW" to (Manufacturer.VW to "Argentina"),
    "WA"  to (Manufacturer.VW to "Alemania"),
    "WV"  to (Manufacturer.VW to "Alemania"),
    // BMW
    "WBA" to (Manufacturer.BMW to "Alemania"),
    "WBS" to (Manufacturer.BMW to "Alemania (M)"),
    "WBY" to (Manufacturer.BMW to "Alemania (EV)"),
    "4US" to (Manufacturer.BMW to "EE.UU."),
    "WB"  to (Manufacturer.BMW to "Alemania"),
    // Mercedes-Benz
    "WDD" to (Manufacturer.MERCEDES to "Alemania"),
    "WDB" to (Manufacturer.MERCEDES to "Alemania"),
    "WDC" to (Manufacturer.MERCEDES to "Alemania"),
    "WD"  to (Manufacturer.MERCEDES to "Alemania"),
    // Hyundai
    "KMH" to (Manufacturer.HYUNDAI to "Corea"),
    "KM8" to (Manufacturer.HYUNDAI to "Corea"),
    "5NM" to (Manufacturer.HYUNDAI to "EE.UU."),
    "KM"  to (Manufacturer.HYUNDAI to "Corea"),
    // Kia
    "KNA" to (Manufacturer.KIA to "Corea"),
    "KND" to (Manufacturer.KIA to "Corea"),
    // Nissan
    "JN1" to (Manufacturer.NISSAN to "Japón"),
    "1N4" to (Manufacturer.NISSAN to "EE.UU."),
    "JN"  to (Manufacturer.NISSAN to "Japón"),
    // Mazda
    "JM1" to (Manufacturer.MAZDA to "Japón"),
    "JM"  to (Manufacturer.MAZDA to "Japón"),
    // Subaru
    "JF1" to (Manufacturer.SUBARU to "Japón"),
    "JF2" to (Manufacturer.SUBARU to "Japón"),
    "4S3" to (Manufacturer.SUBARU to "EE.UU.")
)

@Singleton
class VINDecoder @Inject constructor() {

    private val cache = LruCache<String, VinInfo>(100)

    /**
     * Decodifica un VIN y retorna información del fabricante y año de modelo.
     *
     * @param vin VIN completo de 17 caracteres (o al menos 3).
     * @return [VinInfo] con fabricante detectado y metadatos.
     */
    fun decodeVin(vin: String): VinInfo {
        val normalized = vin.trim().uppercase()
        cache.get(normalized)?.let { return it }

        val wmi = normalized.take(3)
        val (manufacturer, country) = resolveManufacturer(wmi)
        val modelYear = if (normalized.length >= 10) {
            MODEL_YEAR_TABLE[normalized[9]]
        } else null

        val result = VinInfo(
            vin              = normalized,
            wmi              = wmi,
            manufacturer     = manufacturer,
            modelYear        = modelYear,
            assemblyCountry  = country
        )
        cache.put(normalized, result)
        println("[VINDecoder] VIN=$normalized → ${manufacturer.displayName} ($country) año=$modelYear")
        return result
    }

    private fun resolveManufacturer(wmi: String): Pair<Manufacturer, String> {
        // Buscar primero por prefijo de 3, luego 2 chars
        val entry = WMI_MAP.firstOrNull { (prefix, _) -> wmi.startsWith(prefix) }
        return entry?.second ?: (Manufacturer.GENERIC to "Desconocido")
    }

    /** Detecta el fabricante a partir de un VIN. */
    fun detectManufacturer(vin: String): Manufacturer = decodeVin(vin).manufacturer

    /** Limpia el caché de VINs decodificados. */
    fun clearCache() { cache.evictAll() }
}
