package com.obelus.domain.model

sealed class EngineeringProfile(
    val id: String,
    val name: String,
    val description: String,
    val technology: String,
    val pids: List<DdtParameter> = emptyList(),
    val resets: List<DdtCommand> = emptyList()
) {
    object CvtJatco : EngineeringProfile(
        id = "cvt_jatco",
        name = "CVT Jatco Analysis",
        description = "Advanced monitoring for Nissan/Renault/Mitsubishi CVTs",
        technology = "CVT7/CVT8",
        pids = listOf(
            DdtParameter("PRI_PRESSURE", "Primary Pressure", 3, 0, 16, 0f, 100f, "bar", 0.1f),
            DdtParameter("SEC_PRESSURE", "Secondary Pressure", 5, 0, 16, 0f, 100f, "bar", 0.1f),
            DdtParameter("CVT_TEMP", "Transmission Oil Temp", 7, 0, 8, -40f, 215f, "°C", 1f, -40f),
            DdtParameter("JUDDER_CNT", "Judder Vibration Counter", 8, 0, 8, 0f, 255f, "cnt", 1f),
            DdtParameter("RATIO_ACT", "Actual Gear Ratio", 10, 0, 16, 0f, 10f, ":1", 0.001f)
        ),
        resets = listOf(
            DdtCommand("RESET_DEGRADATION", "Reset Oil Degradation Level", "2E 01 02 00"),
            DdtCommand("RELEARN_POINTS", "Relearn Shift Points", "31 01 0F 01")
        )
    )

    object GdiEngine : EngineeringProfile(
        id = "gdi_engine",
        name = "GDI Engine Pro",
        description = "High-pressure fuel and injection analysis",
        technology = "Direct Injection",
        pids = listOf(
            DdtParameter("RAIL_PRESS", "Fuel Rail Pressure", 3, 0, 16, 0f, 200f, "MPa", 0.01f),
            DdtParameter("INJ_TIME", "Injection Pulse Width", 5, 0, 16, 0f, 50f, "ms", 0.001f),
            DdtParameter("CLEAN_STAT", "Injector Cleaning Status", 7, 0, 8, 0f, 1f, "", 1f, valueMap = mapOf(0 to "Normal", 1 to "Cleaning Active")),
            DdtParameter("STFT_CYL1", "Short Term Fuel Trim Cyl 1", 8, 0, 8, -25f, 25f, "%", 0.1f, -12.8f)
        ),
        resets = listOf(
            DdtCommand("INJ_REGEN", "Injectors Software Cleaning", "31 01 AA 01")
        )
    )

    object HybridHev : EngineeringProfile(
        id = "hybrid_hev",
        name = "Hybrid/EV System",
        description = "High Voltage battery and Motor metrics",
        technology = "HEV / PHEV",
        pids = listOf(
            DdtParameter("HV_VOLT", "HV Battery Voltage", 3, 0, 16, 0f, 450f, "V", 0.1f),
            DdtParameter("HV_CURR", "HV Battery Current", 5, 0, 16, -200f, 200f, "A", 0.1f, isSigned = true),
            DdtParameter("BATT_TEMP", "Max Module Temp", 7, 0, 8, -40f, 100f, "°C", 1f, -40f),
            DdtParameter("SOH", "State of Health", 8, 0, 8, 0f, 100f, "%", 1f),
            DdtParameter("CONT_STAT", "HV Contactor Status", 9, 0, 8, 0f, 1f, "", 1f, valueMap = mapOf(0 to "Open", 1 to "Closed"))
        ),
        resets = listOf(
            DdtCommand("CELL_BALANCE", "Force Cell Balancing", "31 01 BB 01")
        )
    )

    object DctTransmission : EngineeringProfile(
        id = "dct_transmission",
        name = "DCT Transmission",
        description = "Dual Clutch Transmission monitoring",
        technology = "Dual Clutch",
        pids = listOf(
            DdtParameter("CLUTCH1_TEMP", "Clutch 1 Temp", 3, 0, 8, 0f, 300f, "°C", 1f),
            DdtParameter("CLUTCH2_TEMP", "Clutch 2 Temp", 4, 0, 8, 0f, 300f, "°C", 1f),
            DdtParameter("PRESS_CTRL", "Clutch Pressure Control", 5, 0, 16, 0f, 60f, "bar", 0.1f)
        ),
        resets = listOf(
            DdtCommand("CLUTCH_KISS", "Clutch Point Adaptation", "31 01 CC 01")
        )
    )

    object Skyactiv : EngineeringProfile(
        id = "skyactiv",
        name = "Mazda Skyactiv",
        description = "Specific high compression engine metrics",
        technology = "Skyactiv-G / D",
        pids = listOf(
            DdtParameter("COMP_RATIO", "Effective Comp Ratio", 3, 0, 16, 0f, 20f, ":1", 0.1f),
            DdtParameter("VVT_EXH", "Exhaust VVT Angle", 5, 0, 16, -20f, 50f, "°", 0.1f)
        )
    )

    companion object {
        val all = listOf(CvtJatco, GdiEngine, HybridHev, DctTransmission, Skyactiv)
        
        fun fromVin(vin: String): EngineeringProfile? {
            return when {
                vin.startsWith("1N") || vin.startsWith("3N") || vin.startsWith("JN") -> CvtJatco // Nissan
                vin.startsWith("KM") || vin.startsWith("KN") -> GdiEngine // Hyundai/Kia
                vin.startsWith("JM") -> Skyactiv // Mazda
                vin.contains("HYBRID") -> HybridHev // Fallback keyword search
                else -> null
            }
        }
    }
}
