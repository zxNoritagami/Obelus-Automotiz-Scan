package com.obelus.data.protocol.uds

/**
 * Interfaz que define el contrato para cualquier algoritmo de Security Access UDS (SID 0x27).
 *
 * ## Flujo UDS Security Access
 * 1. Tester → ECU: 27 `accessLevel`           (Request Seed)
 * 2. ECU → Tester: 67 `accessLevel` `seed...`  (Seed Response)
 * 3. Tester → ECU: 27 `accessLevel+1` `key...` (Send Key)
 * 4. ECU → Tester: 67 `accessLevel+1`          (Positive Response)
 *
 * ## Convención de niveles (ISO 14229)
 * | Level | Descripción                  |
 * |-------|------------------------------|
 * | 0x01  | Programming Session          |
 * | 0x03  | Extended Diagnostics         |
 * | 0x05  | Safety Mode / Plant          |
 * | 0x09  | EOL / End-of-Line            |
 * | 0x0F  | Supplier-specific            |
 *
 * NOTA: El nivel de Send Key siempre es accessLevel + 1 (par).
 * Ejemplo: Request Seed Level=0x01 → Send Key Level=0x02.
 *
 * ## Estado de implementación
 * Los algoritmos específicos requieren ingeniería inversa de cada ECU.
 * Las implementaciones actuales son PLACEHOLDER y devuelven ByteArray vacío.
 * Cuando obtengas el algoritmo real, solo modifica [calculateKey] en la clase
 * correspondiente sin cambiar nada más en la arquitectura.
 */
interface SecurityAlgorithm {

    /**
     * Nombre legible del algoritmo, para mostrar en logs y UI.
     * Ejemplo: "VAG Access Level 01 (Programming)"
     */
    val name: String

    /**
     * Nivel de acceso UDS para el que aplica este algoritmo.
     * Corresponde al subFunction del Request Seed (debe ser impar).
     */
    val accessLevel: Int

    /**
     * Calcula la Key a partir del Seed recibido de la ECU.
     *
     * @param seed    Bytes del seed recibidos en la respuesta 0x67
     * @param level   Nivel de acceso (mismo que [accessLevel])
     * @return        Key calculada para enviar en 0x27 [level+1]
     *                Devuelve ByteArray(0) si el algoritmo no está implementado.
     */
    fun calculateKey(seed: ByteArray, level: Int): ByteArray

    /**
     * True si el algoritmo está realmente implementado (no es placeholder).
     * La UI puede usar esto para advertir al usuario.
     */
    val isImplemented: Boolean get() = false
}

/**
 * Resultado del cálculo de una key.
 * Permite transportar la key o una razón de falla.
 */
sealed class KeyResult {
    data class Success(val key: ByteArray) : KeyResult()
    data class NotImplemented(val algorithmName: String) : KeyResult()
    data class Error(val message: String) : KeyResult()
}
