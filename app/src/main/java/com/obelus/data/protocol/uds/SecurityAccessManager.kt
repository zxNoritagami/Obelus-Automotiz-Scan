package com.obelus.data.protocol.uds

import android.util.Log
import com.obelus.obelusscan.data.protocol.UdsNegativeResponseException
import com.obelus.obelusscan.data.protocol.UdsProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Códigos de Respuesta Negativa (NRC) UDS relevantes para Security Access.
 * Referencia: ISO 14229-1 Tabla A.1
 */
enum class SecurityNrc(val code: Int, val labelEs: String, val advice: String) {
    GENERAL_REJECT          (0x10, "Rechazo general",            "La ECU rechazó la solicitud. Verifica sesión activa."),
    SERVICE_NOT_SUPPORTED   (0x11, "Servicio no soportado",      "Esta ECU no implementa Security Access 0x27."),
    SUBFUNCTION_NOT_SUPPORTED(0x12,"Subfunción no soportada",    "Nivel de acceso no soportado por esta ECU."),
    INCORRECT_MSG_FORMAT    (0x13, "Formato de mensaje incorrecto","Longitud de payload incorrecta."),
    CONDITIONS_NOT_CORRECT  (0x22, "Condiciones incorrectas",    "Activa la sesión correcta antes (01 10 02 para programming)."),
    REQUEST_SEQUENCE_ERROR  (0x24, "Error de secuencia",         "Primero solicita el Seed antes de enviar la Key."),
    REQUEST_OUT_OF_RANGE    (0x31, "Solicitud fuera de rango",   "Nivel de acceso o parámetro fuera del rango ECU."),
    SECURITY_ACCESS_DENIED  (0x33, "Acceso de seguridad denegado","La ECU denegó el acceso. Puede estar bloqueada."),
    INVALID_KEY             (0x35, "Key inválida",               "La Key calculada no coincide. Verifica el algoritmo o reingrésala."),
    EXCEEDED_ATTEMPTS       (0x36, "Intentos excedidos",         "Demasiados intentos fallidos. Espera o reinicia ECU."),
    TIME_DELAY_NOT_EXPIRED  (0x37, "Tiempo de espera no cumplido","Espera antes de reintentar (delay anti-brute-force)."),
    UNKNOWN                 (0xFF, "NRC desconocido",            "Consulta la documentación del fabricante."),
    ;

    companion object {
        fun fromCode(code: Int): SecurityNrc =
            entries.find { it.code == code } ?: UNKNOWN
    }
}

/**
 * Estado del flujo Security Access — emitido hacia la UI.
 */
sealed class SecurityAccessState {
    object Idle : SecurityAccessState()
    object RequestingSeed : SecurityAccessState()
    data class SeedReceived(val seed: ByteArray, val level: Int) : SecurityAccessState() {
        val seedHex: String get() = seed.toHexString()
    }
    object SendingKey : SecurityAccessState()
    data class AccessGranted(val level: Int) : SecurityAccessState()
    data class NegativeResponse(val nrc: SecurityNrc, val rawCode: Int) : SecurityAccessState()
    data class Error(val message: String) : SecurityAccessState()
}

/**
 * Registro de un intento de Security Access (para el historial en UI).
 */
data class SecurityAccessAttempt(
    val level: Int,
    val seedHex: String,
    val keyHex: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Gestor del flujo completo de UDS Security Access (SID 0x27).
 *
 * Flujo principal:
 * 1. [requestSeed] → ECU responde con seed → [SecurityAccessState.SeedReceived]
 * 2. La UI/algoritmo calcula la key
 * 3. [sendKey] → ECU responde OK → [SecurityAccessState.AccessGranted]
 *    o ECU responde NRC → [SecurityAccessState.NegativeResponse]
 *
 * Soporta entrada de key manual o cálculo automático vía [SecurityAlgorithm].
 */
@Singleton
class SecurityAccessManager @Inject constructor(
    private val udsProtocol: UdsProtocol
) {
    companion object {
        private const val TAG = "SecurityAccessManager"
        const val SID_SECURITY_ACCESS = 0x27
        const val ECU_ID_DEFAULT = 0x7E0    // ECM principal (ISO 15765-3)
    }

    // Historial de intentos (en memoria, no persistido)
    val attemptHistory = mutableListOf<SecurityAccessAttempt>()

    // Último seed recibido para validación de secuencia
    private var lastSeed: ByteArray? = null
    private var lastSeedLevel: Int = -1

    /**
     * Paso 1: Solicita el Seed al ECU para el nivel de acceso dado.
     *
     * @param ecuId        ID del ECU (ej: 0x7E0 para ECM)
     * @param accessLevel  Nivel de acceso impar (0x01, 0x03, 0x05...)
     * @return [SecurityAccessState.SeedReceived] o un estado de error
     */
    suspend fun requestSeed(
        ecuId: Int = ECU_ID_DEFAULT,
        accessLevel: Int
    ): SecurityAccessState = withContext(Dispatchers.IO) {

        Log.i(TAG, "Requesting seed: ECU=0x%04X Level=0x%02X".format(ecuId, accessLevel))

        val result = udsProtocol.sendUdsRequest(
            ecuId       = ecuId,
            service     = SID_SECURITY_ACCESS,
            subFunction = accessLevel
        )

        return@withContext result.fold(
            onSuccess = { response ->
                // Respuesta positiva: 0x67 [level] [seed bytes...]
                // response.data[0] = level echo, data[1..] = seed
                if (response.data.size < 2) {
                    SecurityAccessState.Error("Respuesta de seed vacía o demasiado corta")
                } else {
                    val seed = response.data.copyOfRange(1, response.data.size)
                    lastSeed = seed
                    lastSeedLevel = accessLevel
                    Log.i(TAG, "Seed received: ${seed.toHexString()}")
                    SecurityAccessState.SeedReceived(seed, accessLevel)
                }
            },
            onFailure = { ex ->
                handleException(ex)
            }
        )
    }

    /**
     * Paso 2: Envía la Key al ECU.
     *
     * @param ecuId       ID del ECU
     * @param accessLevel Nivel impar del seed solicitado (key level = accessLevel + 1)
     * @param key         Key calculada (ByteArray)
     * @return [SecurityAccessState.AccessGranted] o un estado de error
     */
    suspend fun sendKey(
        ecuId: Int = ECU_ID_DEFAULT,
        accessLevel: Int,
        key: ByteArray
    ): SecurityAccessState = withContext(Dispatchers.IO) {

        val keyLevel = accessLevel + 1  // Nivel par para send key
        Log.i(TAG, "Sending key: Level=0x%02X Key=%s".format(keyLevel, key.toHexString()))

        val result = udsProtocol.sendUdsRequest(
            ecuId       = ecuId,
            service     = SID_SECURITY_ACCESS,
            subFunction = keyLevel,
            data        = key
        )

        val state = result.fold(
            {
                Log.i(TAG, "✅ Security Access GRANTED - Level=0x%02X".format(keyLevel))
                SecurityAccessState.AccessGranted(keyLevel)
            },
            { ex ->
                handleException(ex)
            }
        )

        // Registrar intento en historial
        attemptHistory.add(SecurityAccessAttempt(
            level    = accessLevel,
            seedHex  = lastSeed?.toHexString() ?: "N/A",
            keyHex   = key.toHexString(),
            result   = when (state) {
                is SecurityAccessState.AccessGranted    -> "✅ Acceso concedido"
                is SecurityAccessState.NegativeResponse -> "❌ NRC 0x%02X: %s".format(state.rawCode, state.nrc.labelEs)
                else                                   -> "❌ Error"
            }
        ))

        return@withContext state
    }

    /**
     * Flujo completo: Seed → calculateKey automático → sendKey.
     * Útil cuando ya tienes el algoritmo real implementado.
     *
     * @param ecuId     ID del ECU
     * @param algorithm Implementación del algoritmo de cálculo
     * @return Estado final
     */
    suspend fun performAutoAccess(
        ecuId: Int = ECU_ID_DEFAULT,
        algorithm: SecurityAlgorithm
    ): SecurityAccessState {
        // Paso 1: Seed
        val seedState = requestSeed(ecuId, algorithm.accessLevel)
        if (seedState !is SecurityAccessState.SeedReceived) return seedState

        // Paso 2: Calcular key
        if (!algorithm.isImplemented) {
            return SecurityAccessState.Error(
                "Algoritmo no implementado: ${algorithm.name}. " +
                "Por favor, ingresa la key manualmente."
            )
        }
        val key = algorithm.calculateKey(seedState.seed, algorithm.accessLevel)
        if (key.isEmpty()) return SecurityAccessState.Error("Cálculo de key devolvió vacío")

        // Paso 3: Enviar key
        return sendKey(ecuId, algorithm.accessLevel, key)
    }

    /** Parsea Key desde string hexadecimal ingresado manualmente en UI */
    fun parseKeyFromHexString(hexString: String): Result<ByteArray> {
        return try {
            val clean = hexString.replace(" ", "").replace(":", "")
            if (clean.isEmpty()) return Result.failure(Exception("Key vacía"))
            if (clean.length % 2 != 0) return Result.failure(Exception("Longitud hex inválida"))
            val bytes = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(Exception("Formato hex inválido: ${e.message}"))
        }
    }

    private fun handleException(ex: Throwable): SecurityAccessState {
        return when (ex) {
            is UdsNegativeResponseException -> {
                val nrc = SecurityNrc.fromCode(ex.nrc)
                Log.w(TAG, "NRC 0x%02X: %s".format(ex.nrc, nrc.labelEs))
                SecurityAccessState.NegativeResponse(nrc, ex.nrc)
            }
            else -> {
                Log.e(TAG, "Security access error: ${ex.message}", ex)
                SecurityAccessState.Error(ex.message ?: "Error desconocido")
            }
        }
    }
}
