package com.obelus.data.protocol.uds

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository que expone el dominio de Security Access a la capa de presentación.
 *
 * Agrega:
 * - Catálogo de niveles de acceso disponibles
 * - Selección dinámica de algoritmo por fabricante y nivel
 * - Delegación al [SecurityAccessManager] para la comunicación real
 *
 * La UI solo habla con este Repository, nunca directamente con
 * SecurityAccessManager ni UdsProtocol.
 */
@Singleton
class UdsSecurityRepository @Inject constructor(
    private val securityAccessManager: SecurityAccessManager
) {
    companion object {
        private const val TAG = "UdsSecurityRepository"
    }

    /**
     * Catálogo de niveles UDS Security Access más comunes.
     * La UI muestra esta lista para que el usuario seleccione.
     */
    val availableLevels: List<SecurityAccessLevel> = listOf(
        SecurityAccessLevel(0x01, "Programming Session",    "Programación / Flash de ECU"),
        SecurityAccessLevel(0x03, "Extended Diagnostics",   "Diagnóstico extendido (codificación, adaptaciones)"),
        SecurityAccessLevel(0x05, "Safety Mode",            "Modo seguro / ajustes de planta"),
        SecurityAccessLevel(0x07, "End of Line",            "Configuración de fin de línea"),
        SecurityAccessLevel(0x09, "Development",            "Modo desarrollador"),
        SecurityAccessLevel(0x0B, "Supplier Level 1",       "Nivel proveedor 1"),
        SecurityAccessLevel(0x0D, "Supplier Level 2",       "Nivel proveedor 2"),
        SecurityAccessLevel(0x0F, "Supplier Specific",      "Nivel específico del proveedor")
    )

    /** Mapa de algoritmos disponibles: manufacturer → level → SecurityAlgorithm */
    private val algorithmRegistry: Map<String, Map<Int, SecurityAlgorithm>> = mapOf(
        "VAG" to mapOf(
            0x01 to VagSecurityAlgorithm.PROGRAMMING,
            0x03 to VagSecurityAlgorithm.EXTENDED_DIAG,
            0x05 to VagSecurityAlgorithm.SAFETY,
            0x09 to VagSecurityAlgorithm.EOL,
            0x0F to VagSecurityAlgorithm.SUPPLIER
        ),
        "BMW" to mapOf(
            0x01 to BmwSecurityAlgorithm.PROGRAMMING,
            0x03 to BmwSecurityAlgorithm.EXTENDED_DIAG,
            0x05 to BmwSecurityAlgorithm.CUSTOMER_CODING,
            0x0F to BmwSecurityAlgorithm.FACTORY_MODE
        ),
        "TOYOTA" to mapOf(
            0x01 to ToyotaSecurityAlgorithm.PROGRAMMING,
            0x03 to ToyotaSecurityAlgorithm.EXTENDED_DIAG,
            0x0F to ToyotaSecurityAlgorithm.SUPPLIER
        )
    )

    /**
     * Obtiene el algoritmo para una combinación fabricante + nivel.
     * Si no hay algoritmo específico, devuelve uno genérico.
     */
    fun getAlgorithm(manufacturer: String, level: Int): SecurityAlgorithm {
        return algorithmRegistry[manufacturer.uppercase()]?.get(level)
            ?: GenericSecurityAlgorithm(level)
    }

    /**
     * Solicita el seed al ECU (Paso 1 del flujo Security Access).
     *
     * @param ecuId CAN ID del ECU destino (default: 0x7E0 ECM)
     * @param level Nivel de acceso (impar, ver [availableLevels])
     */
    suspend fun requestSeed(ecuId: Int = 0x7E0, level: Int): SecurityAccessState {
        Log.d(TAG, "requestSeed - ECU=0x%04X Level=0x%02X".format(ecuId, level))
        return securityAccessManager.requestSeed(ecuId, level)
    }

    /**
     * Envía la Key al ECU (Paso 2 del flujo Security Access).
     *
     * @param ecuId  CAN ID del ECU destino
     * @param level  Mismo nivel usado en [requestSeed]
     * @param keyHex Key en formato hexadecimal (ej: "AA BB CC DD")
     */
    suspend fun sendKeyHex(ecuId: Int = 0x7E0, level: Int, keyHex: String): SecurityAccessState {
        val keyResult = securityAccessManager.parseKeyFromHexString(keyHex)
        if (keyResult.isFailure) {
            return SecurityAccessState.Error(keyResult.exceptionOrNull()?.message ?: "Key inválida")
        }
        return securityAccessManager.sendKey(ecuId, level, keyResult.getOrThrow())
    }

    /**
     * Flujo automático si hay algoritmo implementado.
     */
    suspend fun performAutoAccess(ecuId: Int, manufacturer: String, level: Int): SecurityAccessState {
        val algorithm = getAlgorithm(manufacturer, level)
        return securityAccessManager.performAutoAccess(ecuId, algorithm)
    }

    /** Historial de intentos de Security Access */
    val attemptHistory: List<SecurityAccessAttempt>
        get() = securityAccessManager.attemptHistory.toList().reversed()
}

/**
 * Representa un nivel de Security Access para mostrar en la UI.
 */
data class SecurityAccessLevel(
    val level: Int,
    val name: String,
    val description: String
) {
    /** Nivel par correspondiente para el Send Key */
    val keyLevel: Int get() = level + 1
    val levelHex: String get() = "0x%02X".format(level)
}
