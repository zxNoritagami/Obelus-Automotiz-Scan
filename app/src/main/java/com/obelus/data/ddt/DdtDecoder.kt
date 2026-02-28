package com.obelus.data.ddt

import android.util.Log
import com.obelus.domain.model.DdtParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Robust Diagnostic Engine for decoding complex ECU responses.
 * Optimized for high performance and accuracy.
 */
class DdtDecoder {

    private val TAG = "DdtDecoder"

    /**
     * Decodes a parameter from a raw hex response string.
     * Runs on Dispatchers.Default for high performance bit extraction.
     */
    suspend fun decode(hexResponse: String, parameter: DdtParameter): Float = withContext(Dispatchers.Default) {
        val cleanHex = hexResponse.replace(" ", "").uppercase()
        
        // 4. VALIDACIÓN DE ROBUSTEZ: required bytes calculation
        val requiredBytes = parameter.byteOffset + ((parameter.bitOffset + parameter.length - 1) / 8)
        if (cleanHex.length < requiredBytes * 2) {
            Log.w(TAG, "Response too short for ${parameter.name}. Expected $requiredBytes bytes.")
            return@withContext 0f
        }

        val bytes = hexToBytes(cleanHex)
        
        // 1. Extract raw bits based on endianness (Default: Big Endian Motorola)
        val rawValue = if (parameter.isLittleEndian) {
            extractBitsLittleEndian(bytes, parameter.byteOffset - 1, parameter.bitOffset, parameter.length)
        } else {
            extractBitsBigEndian(bytes, parameter.byteOffset - 1, parameter.bitOffset, parameter.length)
        }

        // 2. Handle signed interpretation
        var processedValue = if (parameter.isSigned) {
            applySign(rawValue, parameter.length)
        } else {
            rawValue
        }

        // 3. Apply formula: (raw * step) + offset
        // 4. VALIDACIÓN DE ROBUSTEZ: handle null/zero step/offset
        val step = if (parameter.step == 0f) 1.0f else parameter.step
        val finalValue = (processedValue.toFloat() * step) + parameter.offset

        // 4. Logging detallado en modo debug
        Log.d(TAG, "DECODE [${parameter.name}]: Trama=$cleanHex -> RawBits=$rawValue -> Value=$finalValue")
        
        return@withContext finalValue
    }

    private fun extractBitsBigEndian(bytes: ByteArray, startByte: Int, bitOffset: Int, length: Int): Long {
        var value = 0L
        for (i in 0 until length) {
            val totalBitPos = (startByte * 8) + bitOffset + i
            val byteIdx = totalBitPos / 8
            val bitIdx = 7 - (totalBitPos % 8)
            
            if (byteIdx < bytes.size) {
                val bit = (bytes[byteIdx].toInt() shr bitIdx) and 1
                if (bit == 1) {
                    value = value or (1L shl (length - 1 - i))
                }
            }
        }
        return value
    }

    private fun extractBitsLittleEndian(bytes: ByteArray, startByte: Int, bitOffset: Int, length: Int): Long {
        var value = 0L
        for (i in 0 until length) {
            val totalBitPos = (startByte * 8) + bitOffset + i
            val byteIdx = totalBitPos / 8
            val bitIdx = totalBitPos % 8
            
            if (byteIdx < bytes.size) {
                val bit = (bytes[byteIdx].toInt() shr bitIdx) and 1
                if (bit == 1) {
                    value = value or (1L shl i)
                }
            }
        }
        return value
    }

    private fun applySign(value: Long, length: Int): Long {
        val mask = 1L shl (length - 1)
        return if ((value and mask) != 0L) {
            value or ((-1L) shl length)
        } else {
            value
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    fun getDisplayValue(value: Float, parameter: DdtParameter): String {
        val intValue = value.toInt()
        return if (parameter.valueMap.containsKey(intValue)) {
            parameter.valueMap[intValue]!!
        } else {
            String.format(Locale.US, "%.2f %s", value, parameter.unit).trim()
        }
    }
}
