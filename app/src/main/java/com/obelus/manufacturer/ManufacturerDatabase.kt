package com.obelus.manufacturer

// ─────────────────────────────────────────────────────────────────────────────
// ManufacturerDatabase.kt
// Base de datos estática de PIDs específicos de fabricante (Modo 21/22).
// Extensible: añadir nuevas entradas sin modificar el repositorio.
// ─────────────────────────────────────────────────────────────────────────────

object ManufacturerDatabase {

    // Atajo de construcción para modo 22
    private fun pid22(
        dataId: String,
        mfr: Manufacturer,
        pid: String,
        description: String,
        parser: ResponseType,
        scale: Float = 1f,
        offset: Float = 0f,
        unit: String = "",
        normalMin: Float? = null,
        normalMax: Float? = null,
        model: String? = null,
        years: IntRange? = null,
        encodingTable: Map<Int, String> = emptyMap()
    ) = ManufacturerData(
        dataId = dataId, manufacturer = mfr, model = model, years = years,
        description = description, mode = "22", pid = pid,
        responseParser = parser, scale = scale, offset = offset,
        unit = unit, normalMin = normalMin, normalMax = normalMax,
        encodingTable = encodingTable
    )

    // Atajo para modo 21
    private fun pid21(
        dataId: String,
        mfr: Manufacturer,
        pid: String,
        description: String,
        parser: ResponseType,
        scale: Float = 1f,
        offset: Float = 0f,
        unit: String = "",
        normalMin: Float? = null,
        normalMax: Float? = null,
        model: String? = null,
        years: IntRange? = null
    ) = ManufacturerData(
        dataId = dataId, manufacturer = mfr, model = model, years = years,
        description = description, mode = "21", pid = pid,
        responseParser = parser, scale = scale, offset = offset,
        unit = unit, normalMin = normalMin, normalMax = normalMax
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // TOYOTA
    // ═══════════════════════════════════════════════════════════════════════════
    private val toyota: List<ManufacturerData> = listOf(
        pid22("TOYOTA_OIL_PRESSURE",
            Manufacturer.TOYOTA, "0115",
            "Presión de aceite de motor",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.1f, unit = "kPa", normalMin = 100f, normalMax = 620f
        ),
        pid22("TOYOTA_CVT_TEMP",
            Manufacturer.TOYOTA, "0241",
            "Temperatura del aceite de transmisión CVT",
            ResponseType.SINGLE_BYTE,
            scale = 1f, offset = -40f, unit = "°C", normalMin = 60f, normalMax = 120f,
            model = "CVT"
        ),
        pid22("TOYOTA_VVT_ANGLE_INTAKE",
            Manufacturer.TOYOTA, "2101",
            "Ángulo de la corrediza de admisión VVT-i (Banco 1)",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.5f, offset = -64f, unit = "°CA", normalMin = -5f, normalMax = 50f
        ),
        pid22("TOYOTA_VVT_ANGLE_EXHAUST",
            Manufacturer.TOYOTA, "2102",
            "Ángulo de la corrediza de escape VVT-i (Banco 1)",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.5f, offset = -64f, unit = "°CA", normalMin = -5f, normalMax = 45f
        ),
        pid22("TOYOTA_KNOCK_1",
            Manufacturer.TOYOTA, "0132",
            "Corrección de avance por detonación Banco 1",
            ResponseType.SINGLE_BYTE,
            scale = 0.75f, offset = -96f, unit = "°CA"
        ),
        pid22("TOYOTA_AIR_FUEL_RATIO",
            Manufacturer.TOYOTA, "011D",
            "Lambda (A/F ratio) Banco 1 Sensor 1",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.0000305f, unit = "λ", normalMin = 0.9f, normalMax = 1.1f
        ),
        pid21("TOYOTA_ODOMETER",
            Manufacturer.TOYOTA, "E601",
            "Lectura del odómetro interno del ECU",
            ResponseType.FOUR_BYTES,
            scale = 0.1f, unit = "km"
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // FORD
    // ═══════════════════════════════════════════════════════════════════════════
    private val ford: List<ManufacturerData> = listOf(
        pid22("FORD_HPFP_PRESSURE",
            Manufacturer.FORD, "1275",
            "Presión de combustible de alta presión (GDI)",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 4f, unit = "kPa", normalMin = 2000f, normalMax = 20000f
        ),
        pid22("FORD_TURBO_INLET_TEMP",
            Manufacturer.FORD, "112A",
            "Temperatura de entrada al turbocompresor",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.1f, offset = -40f, unit = "°C", normalMin = -30f, normalMax = 120f
        ),
        pid22("FORD_BOOST_PRESSURE",
            Manufacturer.FORD, "1135",
            "Presión de sobrealimentación (boost) real",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.01f, unit = "kPa", normalMin = 101f, normalMax = 250f
        ),
        pid22("FORD_OIL_TEMP",
            Manufacturer.FORD, "1143",
            "Temperatura del aceite de motor",
            ResponseType.SINGLE_BYTE,
            scale = 1f, offset = -40f, unit = "°C", normalMin = 80f, normalMax = 130f
        ),
        pid22("FORD_TRANS_TEMP",
            Manufacturer.FORD, "1950",
            "Temperatura de la transmisión automática",
            ResponseType.SINGLE_BYTE,
            scale = 1f, offset = -40f, unit = "°C", normalMin = 60f, normalMax = 120f
        ),
        pid22("FORD_FUEL_INJECT_PW",
            Manufacturer.FORD, "1205",
            "Duración de pulso de inyector (Banco 1)",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.01f, unit = "ms", normalMin = 1f, normalMax = 20f
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // VW / AUDI
    // ═══════════════════════════════════════════════════════════════════════════
    private val vw: List<ManufacturerData> = listOf(
        pid22("VW_OIL_PRESSURE",
            Manufacturer.VW, "1514",
            "Presión de aceite de motor (sensor manométrico)",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.1f, unit = "kPa", normalMin = 150f, normalMax = 700f
        ),
        pid22("VW_DSG_TEMP",
            Manufacturer.VW, "1403",
            "Temperatura del aceite de transmisión DSG",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.1f, offset = -273.1f, unit = "°C", normalMin = 60f, normalMax = 110f,
            model = "DSG"
        ),
        pid22("VW_EGR_POSITION",
            Manufacturer.VW, "1120",
            "Posición de la válvula EGR (apertura real)",
            ResponseType.SINGLE_BYTE,
            scale = 100f / 255f, unit = "%", normalMin = 0f, normalMax = 100f
        ),
        pid22("VW_INJECTION_QUANTITY",
            Manufacturer.VW, "111E",
            "Cantidad inyectada por ciclo (mg/ciclo)",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.01f, unit = "mg/stroke"
        ),
        pid22("VW_LAMBDA_REGULATION",
            Manufacturer.VW, "1130",
            "Estado de regulación lambda (0=OL, 1=CL Lean, 2=CL Rich)",
            ResponseType.SINGLE_BYTE,
            encodingTable = mapOf(0 to "Lazo abierto", 1 to "Lazo cerrado Lean", 2 to "Lazo cerrado Rich")
        ),
        pid22("VW_BOOST_ACTUAL",
            Manufacturer.VW, "1007",
            "Presión real de sobrealimentación",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.01f, unit = "kPa", normalMin = 80f, normalMax = 300f
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // GM (General Motors)
    // ═══════════════════════════════════════════════════════════════════════════
    private val gm: List<ManufacturerData> = listOf(
        pid22("GM_TRANS_TEMP",
            Manufacturer.GM, "1014",
            "Temperatura de la transmisión automática",
            ResponseType.SINGLE_BYTE,
            scale = 1f, offset = -40f, unit = "°C", normalMin = 60f, normalMax = 110f
        ),
        pid22("GM_OIL_PRESSURE",
            Manufacturer.GM, "115A",
            "Presión de aceite de motor",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.1f, unit = "kPa", normalMin = 200f, normalMax = 600f
        ),
        pid22("GM_AFM_STATUS",
            Manufacturer.GM, "1033",
            "Estado del sistema AFM (Gestión Activa de Combustible / V4 mode)",
            ResponseType.BITFIELD,
            encodingTable = mapOf(
                0 to "Todos los cilindros activos",
                1 to "Modo V4/cylinder deactivation activo",
                2 to "AFM desactivado por carga",
                3 to "AFM desactivado por temperatura"
            )
        ),
        pid22("GM_ENGINE_OIL_LIFE",
            Manufacturer.GM, "1046",
            "Vida útil restante del aceite de motor (%)",
            ResponseType.SINGLE_BYTE,
            scale = 1f, unit = "%", normalMin = 0f, normalMax = 100f
        ),
        pid22("GM_FUEL_PUMP_FEEDBACK",
            Manufacturer.GM, "1052",
            "Voltaje de retroalimentación de la bomba de combustible",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.001f, unit = "V", normalMin = 10.5f, normalMax = 14.5f
        ),
        pid22("GM_TIRE_PRESSURE_FL",
            Manufacturer.GM, "C101",
            "Presión neumático delantero izquierdo (TPMS)",
            ResponseType.SINGLE_BYTE,
            scale = 1f, unit = "kPa", normalMin = 200f, normalMax = 280f
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // BMW
    // ═══════════════════════════════════════════════════════════════════════════
    private val bmw: List<ManufacturerData> = listOf(
        pid22("BMW_OIL_TEMP",
            Manufacturer.BMW, "407D",
            "Temperatura del aceite de motor",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.1f, offset = -40f, unit = "°C", normalMin = 80f, normalMax = 140f
        ),
        pid22("BMW_OIL_PRESSURE",
            Manufacturer.BMW, "407C",
            "Presión de aceite de motor (manométrico)",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.1f, unit = "kPa", normalMin = 100f, normalMax = 600f
        ),
        pid22("BMW_FUEL_PRESSURE",
            Manufacturer.BMW, "4087",
            "Presión de inyección (riel de alta presión)",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 16f, unit = "kPa", normalMin = 5000f, normalMax = 20000f
        ),
        pid22("BMW_VANOS_ANGLE_INT",
            Manufacturer.BMW, "4052",
            "Ángulo de VANOS admisión (posición del árbol de levas)",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.1f, offset = -40f, unit = "°CA", normalMin = -5f, normalMax = 60f
        ),
        pid22("BMW_VANOS_ANGLE_EXH",
            Manufacturer.BMW, "4053",
            "Ángulo de VANOS escape",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.1f, offset = -40f, unit = "°CA"
        ),
        pid22("BMW_BATTERY_SOC",
            Manufacturer.BMW, "44E1",
            "Estado de carga de la batería (SOC)",
            ResponseType.SINGLE_BYTE,
            scale = 0.5f, unit = "%", normalMin = 50f, normalMax = 100f
        ),
        pid22("BMW_BOOST_PRESSURE",
            Manufacturer.BMW, "4104",
            "Presión de sobrealimentación (valor real)",
            ResponseType.TWO_BYTES_BIG_ENDIAN,
            scale = 0.01f, unit = "kPa", normalMin = 80f, normalMax = 250f
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // HONDA
    // ═══════════════════════════════════════════════════════════════════════════
    private val honda: List<ManufacturerData> = listOf(
        pid22("HONDA_ATF_TEMP",
            Manufacturer.HONDA, "1105",
            "Temperatura del aceite de transmisión automática",
            ResponseType.SINGLE_BYTE,
            scale = 1f, offset = -40f, unit = "°C", normalMin = 60f, normalMax = 120f
        ),
        pid22("HONDA_VTEC_STATUS",
            Manufacturer.HONDA, "1158",
            "Estado del sistema VTEC (0=bajo, 1=alto)",
            ResponseType.SINGLE_BYTE,
            encodingTable = mapOf(0 to "Levas bajas (economía)", 1 to "Levas altas (potencia)")
        ),
        pid22("HONDA_OIL_PRESSURE",
            Manufacturer.HONDA, "110A",
            "Presión de aceite de motor (switch de presión)",
            ResponseType.SINGLE_BYTE,
            encodingTable = mapOf(0 to "Sin presión ⚠️", 1 to "Presión OK ✅")
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // FULL CATALOGUE
    // ═══════════════════════════════════════════════════════════════════════════

    val all: List<ManufacturerData> =
        toyota + ford + vw + gm + bmw + honda

    /**
     * Retorna los datos conocidos para un fabricante específico,
     * opcionalmente filtrados por modelo y año.
     */
    fun getFor(
        manufacturer: Manufacturer,
        model: String? = null,
        year: Int? = null
    ): List<ManufacturerData> = all.filter { d ->
        d.manufacturer == manufacturer &&
        (model == null || d.model == null || d.model.equals(model, ignoreCase = true)) &&
        (year  == null || d.years == null || year in d.years)
    }

    /** Busca un dato por ID. */
    fun findById(dataId: String): ManufacturerData? =
        all.firstOrNull { it.dataId == dataId }
}
