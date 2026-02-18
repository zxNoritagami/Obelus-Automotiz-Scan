package com.obelus.domain.model

/**
 * Representa un Parameter ID (PID) de OBD2.
 * Contiene la metainformación y la lógica de decodificación.
 */
sealed class ObdPid(
    val pidCode: String,
    val description: String,
    val unit: String,
    val minValue: Float,
    val maxValue: Float
) {
    /**
     * Calcula el valor real a partir de los bytes crudos.
     * @param bytes Lista de bytes de datos (A, B, C, D...)
     */
    abstract fun calculate(bytes: List<Int>): Float

    // --- PIDs EXISTENTES (Replicados de Obd2Decoder) ---

    object RPM : ObdPid("0C", "Engine RPM", "rpm", 0f, 16383.75f) {
        // ((A * 256) + B) / 4
        override fun calculate(bytes: List<Int>): Float {
            if (bytes.size < 2) return 0f
            return ((bytes[0] * 256) + bytes[1]) / 4f
        }
    }

    object SPEED : ObdPid("0D", "Vehicle Speed", "km/h", 0f, 255f) {
        // A
        override fun calculate(bytes: List<Int>): Float {
            if (bytes.isEmpty()) return 0f
            return bytes[0].toFloat()
        }
    }

    object COOLANT_TEMP : ObdPid("05", "Coolant Temperature", "°C", -40f, 215f) {
        // A - 40
        override fun calculate(bytes: List<Int>): Float {
            if (bytes.isEmpty()) return 0f
            return bytes[0] - 40f
        }
    }

    object ENGINE_LOAD : ObdPid("04", "Calculated Engine Load", "%", 0f, 100f) {
        // (A * 100) / 255
        override fun calculate(bytes: List<Int>): Float {
            if (bytes.isEmpty()) return 0f
            return (bytes[0] * 100f) / 255f
        }
    }

    object THROTTLE_POS : ObdPid("11", "Throttle Position", "%", 0f, 100f) {
        // (A * 100) / 255
        override fun calculate(bytes: List<Int>): Float {
            if (bytes.isEmpty()) return 0f
            return (bytes[0] * 100f) / 255f
        }
    }

    // --- NUEVOS PIDs ---

    object MAF_RATE : ObdPid("10", "Flujo de Aire", "g/s", 0f, 655.35f) {
        // ((A * 256) + B) / 100
        override fun calculate(bytes: List<Int>): Float {
            if (bytes.size < 2) return 0f
            return ((bytes[0] * 256) + bytes[1]) / 100f
        }
    }

    object INTAKE_PRESSURE : ObdPid("0B", "Presión Admisión", "kPa", 0f, 255f) {
        // A
        override fun calculate(bytes: List<Int>): Float {
            if (bytes.isEmpty()) return 0f
            return bytes[0].toFloat()
        }
    }

    object BAROMETRIC_PRESSURE : ObdPid("33", "Presión Barométrica", "kPa", 0f, 255f) {
        // A
        override fun calculate(bytes: List<Int>): Float {
            if (bytes.isEmpty()) return 0f
            return bytes[0].toFloat()
        }
    }

    object FUEL_LEVEL : ObdPid("2F", "Nivel de Combustible", "%", 0f, 100f) {
        // (100 * A) / 255
        override fun calculate(bytes: List<Int>): Float {
            if (bytes.isEmpty()) return 0f
            return (100f * bytes[0]) / 255f
        }
    }

    object AMBIENT_TEMP : ObdPid("46", "Temperatura Ambiente", "°C", -40f, 215f) {
        // A - 40
        override fun calculate(bytes: List<Int>): Float {
            if (bytes.isEmpty()) return 0f
            return bytes[0] - 40f
        }
    }

    companion object {
        fun getByCode(code: String): ObdPid? {
            return when (code) {
                "0C" -> RPM
                "0D" -> SPEED
                "05" -> COOLANT_TEMP
                "04" -> ENGINE_LOAD
                "11" -> THROTTLE_POS
                "10" -> MAF_RATE
                "0B" -> INTAKE_PRESSURE
                "33" -> BAROMETRIC_PRESSURE
                "2F" -> FUEL_LEVEL
                "46" -> AMBIENT_TEMP
                else -> null
            }
        }
    }
}
