package com.obelus.data.protocol.uds

import android.util.Log

// ─────────────────────────────────────────────────────────────────────────────
// VAG (VW / Audi / Seat / Skoda) — Placeholder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Algoritmo Security Access para ECUs del grupo VAG.
 *
 * ESTADO: PLACEHOLDER — necesita ingeniería inversa del algoritmo específico.
 *
 * Notas conocidas (obtenidas de foros de investigación de seguridad automotriz):
 * - Level 0x01 (Programming): Usa SEED de 4 bytes. Algoritmo varía por ECU/modelo.
 * - Level 0x03 (Extended Diag): Usa SEED de 4 bytes. Algoritmo típicamente XOR+rotación.
 * - Algunas ECUs Bosch MED17/MED9 usan algoritmo conocido (VAG SA2).
 *
 * Para implementar el algoritmo real, modifica [calculateKey].
 * No se requiere cambio en ningún otro archivo del sistema.
 */
class VagSecurityAlgorithm(override val accessLevel: Int) : SecurityAlgorithm {

    override val name: String get() = "VAG Security Access Level 0x%02X".format(accessLevel)
    override val isImplemented: Boolean = false

    override fun calculateKey(seed: ByteArray, level: Int): ByteArray {
        Log.w("VagSecurityAlgorithm",
            "⚠️ Algorithm not implemented - needs reverse engineering. " +
            "Level=0x%02X Seed=%s".format(level, seed.toHexString())
        )
        // TODO: Implementar cuando se disponga del algoritmo
        // Ejemplo de algoritmo placeholder (NO FUNCIONAL):
        // return seed.map { (it.toInt() xor 0xAA).toByte() }.toByteArray()
        return ByteArray(0)
    }

    companion object {
        /** Instancias predefinidas para los niveles de acceso VAG más comunes */
        val PROGRAMMING   = VagSecurityAlgorithm(0x01)  // Programming Session
        val EXTENDED_DIAG = VagSecurityAlgorithm(0x03)  // Extended Diagnostics
        val SAFETY        = VagSecurityAlgorithm(0x05)  // Safety Mode
        val EOL           = VagSecurityAlgorithm(0x09)  // End-of-Line
        val SUPPLIER      = VagSecurityAlgorithm(0x0F)  // Supplier-specific
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BMW (incl. Mini) — Placeholder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Algoritmo Security Access para ECUs BMW / Mini.
 *
 * ESTADO: PLACEHOLDER — necesita ingeniería inversa del algoritmo específico.
 *
 * Notas conocidas:
 * - Level 0x01: Usado en DME/DDE (Bosch). SEED de 4 bytes.
 * - Level 0x03: CAS/FRM/CAS4. SEED de 4 bytes.
 * - Algunas ECUs BMW usan "BMW Standard Crypto" basado en DES o AES.
 * - El tool oficial es ISTA/D. Los algoritmos no son públicos.
 */
class BmwSecurityAlgorithm(override val accessLevel: Int) : SecurityAlgorithm {

    override val name: String get() = "BMW Security Access Level 0x%02X".format(accessLevel)
    override val isImplemented: Boolean = false

    override fun calculateKey(seed: ByteArray, level: Int): ByteArray {
        Log.w("BmwSecurityAlgorithm",
            "⚠️ Algorithm not implemented - needs reverse engineering. " +
            "Level=0x%02X Seed=%s".format(level, seed.toHexString())
        )
        return ByteArray(0)
    }

    companion object {
        val PROGRAMMING      = BmwSecurityAlgorithm(0x01)  // Programming
        val EXTENDED_DIAG    = BmwSecurityAlgorithm(0x03)  // Extended
        val CUSTOMER_CODING  = BmwSecurityAlgorithm(0x05)  // Coding
        val FACTORY_MODE     = BmwSecurityAlgorithm(0x0F)  // Factory
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toyota / Lexus — Placeholder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Algoritmo Security Access para ECUs Toyota / Lexus / Prius.
 *
 * ESTADO: PLACEHOLDER — necesita ingeniería inversa del algoritmo específico.
 *
 * Notas conocidas:
 * - Level 0x01: ECM principal. SEED de 4 bytes.
 * - Level 0x03: Extendido (ABS, Airbag). SEED de 4 bytes.
 * - Algunas ECUs older Toyota usan algoritmo simple (suma de bytes del seed + constante).
 * - La tool oficial es Techstream. Los algoritmos varían por año/modelo.
 */
class ToyotaSecurityAlgorithm(override val accessLevel: Int) : SecurityAlgorithm {

    override val name: String get() = "Toyota Security Access Level 0x%02X".format(accessLevel)
    override val isImplemented: Boolean = false

    override fun calculateKey(seed: ByteArray, level: Int): ByteArray {
        Log.w("ToyotaSecurityAlgorithm",
            "⚠️ Algorithm not implemented - needs reverse engineering. " +
            "Level=0x%02X Seed=%s".format(level, seed.toHexString())
        )
        return ByteArray(0)
    }

    companion object {
        val PROGRAMMING   = ToyotaSecurityAlgorithm(0x01)  // Programming
        val EXTENDED_DIAG = ToyotaSecurityAlgorithm(0x03)  // Extended
        val SUPPLIER      = ToyotaSecurityAlgorithm(0x0F)  // Supplier
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Generic — Para pruebas y ECUs desconocidas
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Algoritmo genérico: permite enviar una key calculada externamente
 * (ingresada manualmente por el usuario en la UI).
 *
 * No calcula nada, siempre devuelve ByteArray(0).
 * La UI debe ofrecer la opción de ingresar la key manualmente.
 */
class GenericSecurityAlgorithm(override val accessLevel: Int) : SecurityAlgorithm {
    override val name: String get() = "Genérico / Manual (Nivel 0x%02X)".format(accessLevel)
    override val isImplemented: Boolean = false

    override fun calculateKey(seed: ByteArray, level: Int): ByteArray {
        Log.i("GenericSecurityAlgorithm",
            "ℹ️ Generic placeholder. Enter key manually. Seed=%s".format(seed.toHexString())
        )
        return ByteArray(0)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension helper
// ─────────────────────────────────────────────────────────────────────────────
internal fun ByteArray.toHexString(): String =
    joinToString(" ") { "%02X".format(it) }
