package com.obelus.data.dbc

// ─────────────────────────────────────────────────────────────────────────────
// DbcValidator.kt
// Validaciones estáticas de mensajes y señales de un DbcDatabase.
// Pure Kotlin – sin dependencias de Android.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Proporciona validaciones estáticas sobre los datos parseados de un .dbc.
 *
 * Todas las funciones son puras (sin side-effects) y pueden usarse en tests.
 */
object DbcValidator {

    // ── Rangos válidos ────────────────────────────────────────────────────────

    /** Rango de IDs de frame estándar (11-bit). */
    private val STANDARD_ID_RANGE = 0L..0x7FFL

    /** Rango de IDs de frame extendido (29-bit). */
    private val EXTENDED_ID_RANGE = 0L..0x1FFF_FFFFL

    // ── API pública ────────────────────────────────────────────────────────────

    /**
     * Comprueba que un ID CAN esté en el rango válido para frame estándar (11-bit)
     * o extendido (29-bit).
     *
     * @param id         ID CAN canónico (sin el bit de frame extendido).
     * @param isExtended `true` si el frame es de 29 bits.
     * @return `true` si el ID está en el rango permitido.
     */
    fun validateMessageId(id: Long, isExtended: Boolean = false): Boolean {
        val valid = if (isExtended) id in EXTENDED_ID_RANGE else id in STANDARD_ID_RANGE
        if (!valid) println("[DbcValidator] ID inválido: 0x${"%X".format(id)} (extended=$isExtended)")
        return valid
    }

    /**
     * Detecta señales cuyas posiciones de bit se solapan dentro de un mismo mensaje.
     *
     * Para simplificar, usa el rango [startBit, startBit + bitLength) en coordenadas
     * de Little-Endian. Las señales Big-Endian pueden tener rangos aparentes que se
     * cruzan pero no suponen overlap real; se marcan sólo si sus startBit coinciden.
     *
     * @param signals Lista de señales a analizar (deben pertenecer al mismo mensaje).
     * @return Lista de cadenas descriptivas de los solapamientos encontrados.
     *         Vacía si no hay ninguno.
     */
    fun validateSignalOverlap(signals: List<DbcSignal>): List<String> {
        val warnings = mutableListOf<String>()
        val indexed  = signals.toList()

        for (i in indexed.indices) {
            for (j in i + 1 until indexed.size) {
                val a = indexed[i]
                val b = indexed[j]

                val overlapBits = overlapCount(a, b)
                if (overlapBits > 0) {
                    val msg = "OVERLAP: \"${a.name}\" [${a.startBit}..${a.startBit + a.bitLength - 1}] " +
                              "vs \"${b.name}\" [${b.startBit}..${b.startBit + b.bitLength - 1}] " +
                              "($overlapBits bit(s))"
                    warnings.add(msg)
                    println("[DbcValidator] $msg")
                }
            }
        }
        return warnings.toList()
    }

    /**
     * Verifica que el [scale] de la señal no sea cero (evita división por cero
     * al realizar operaciones inversas: raw = (phys - offset) / scale).
     *
     * También comprueba que [min] ≤ [max] si ambos están presentes.
     *
     * @param signal Señal a validar.
     * @return `true` si los parámetros son coherentes.
     */
    fun validateScaleOffset(signal: DbcSignal): Boolean {
        if (signal.scale == 0.0) {
            println("[DbcValidator] \"${signal.name}\": scale=0 (división por cero al invertir)")
            return false
        }
        val minMax = signal.min != null && signal.max != null && signal.min > signal.max
        if (minMax) {
            println("[DbcValidator] \"${signal.name}\": min(${signal.min}) > max(${signal.max})")
            return false
        }
        return true
    }

    /**
     * Ejecuta todas las validaciones sobre un [DbcDatabase] y devuelve un informe.
     *
     * @param db Base de datos parseada.
     * @return [ValidationReport] con errores y advertencias encontradas.
     */
    fun validate(db: DbcDatabase): ValidationReport {
        val errors   = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        println("[DbcValidator] Validando ${db.messages.size} mensajes, ${db.signalCount} señales…")

        for (msg in db.messages) {
            // Validar ID
            if (!validateMessageId(msg.id, msg.isExtended)) {
                errors.add("Mensaje \"${msg.name}\": ID inválido 0x${"%X".format(msg.id)}")
            }

            // Validar DLC
            if (msg.length !in 0..8) {
                errors.add("Mensaje \"${msg.name}\": DLC=${msg.length} fuera de rango [0,8]")
            }

            // Validar señales individuales
            for (sig in msg.signals) {
                if (!validateScaleOffset(sig)) {
                    errors.add("Señal \"${sig.name}\" en \"${msg.name}\": scale/offset inválido")
                }
                if (sig.startBit < 0 || sig.startBit > 63) {
                    errors.add("Señal \"${sig.name}\": startBit=${sig.startBit} fuera de [0,63]")
                }
                if (sig.bitLength < 1 || sig.bitLength > 64) {
                    errors.add("Señal \"${sig.name}\": bitLength=${sig.bitLength} fuera de [1,64]")
                }
            }

            // Detectar solapamientos dentro del mensaje
            warnings.addAll(validateSignalOverlap(msg.signals))
        }

        // Añadir errores del propio parser
        errors.addAll(db.parseErrors)

        val report = ValidationReport(errors, warnings)
        println("[DbcValidator] Resultado: ${errors.size} error(es), ${warnings.size} advertencia(s).")
        return report
    }

    // ── Privado ───────────────────────────────────────────────────────────────

    /** Devuelve el número de bits en que se solapan dos señales (aprox. Little-Endian). */
    private fun overlapCount(a: DbcSignal, b: DbcSignal): Int {
        val aEnd = a.startBit + a.bitLength
        val bEnd = b.startBit + b.bitLength
        val overlapStart = maxOf(a.startBit, b.startBit)
        val overlapEnd   = minOf(aEnd, bEnd)
        return maxOf(0, overlapEnd - overlapStart)
    }
}

// ── Informe de validación ─────────────────────────────────────────────────────

/**
 * Resultado de [DbcValidator.validate].
 *
 * @param errors   Problemas que hacen el archivo inválido (IDs fuera de rango, etc.).
 * @param warnings Solapamientos u otras anomalías que no impiden el parseo.
 */
data class ValidationReport(
    val errors: List<String>,
    val warnings: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()

    override fun toString(): String =
        "ValidationReport(valid=$isValid errors=${errors.size} warnings=${warnings.size})"
}
