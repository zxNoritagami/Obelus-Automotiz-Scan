package com.obelus.data.security

import java.security.MessageDigest
import kotlin.random.Random

/**
 * Genera OTPs mezclando la identidad del mecánico con entropía matemática y criptografía Base58
 */
object OtpGenerator {

    private val pool = listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+", "-", "=", "[", "]", "{", "}", "|", ";", ":", ",", ".", "<", ">", "?")
    private val multipliers = listOf(3, 13, 24, 36, 48, 60, 72, 84, 96, 108)

    // Alfabeto Base58 Bitcoin (sin 0, O, I, l ambigüos)
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun generateOtp(mechanicName: String): String {
        // a) Validar y limpiar: max 10 chars, solo letras
        var cleanName = mechanicName.filter { it.isLetter() }
        if (cleanName.isEmpty()) cleanName = "Mechanic"
        val clampedName = cleanName.take(10)

        // b & c) Base: construct mathematically weighted string
        val baseBuilder = StringBuilder(clampedName)
        clampedName.forEachIndexed { index, char ->
            val charWeight = char.lowercaseChar() - 'a' + 1 // A=1..Z=26
            val multiplier = multipliers.getOrElse(index) { multipliers.last() }
            val component = charWeight * multiplier
            baseBuilder.append(component)
        }
        val mathBase = baseBuilder.toString()

        // d & e) random separators
        val separators = pool.shuffled(Random(System.currentTimeMillis())).take(10)
        
        val saltBuilder = StringBuilder()
        var sepIndex = 0
        
        // Interleaved scattering of mathematical base and separators
        for (i in mathBase.indices) {
            saltBuilder.append(mathBase[i])
            if (i % 2 == 0 && sepIndex < separators.size) {
                saltBuilder.append(separators[sepIndex])
                sepIndex++
            }
        }
        
        val finalString = saltBuilder.toString()

        // f & g) SHA-256 -> bytes -> Base58 12 chars
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(finalString.toByteArray(Charsets.UTF_8))
        
        val base58 = encodeBase58(hashBytes.take(12).toByteArray())

        // h) Return exactly 12 chars (padded or clamped)
        return construct12CharToken(base58)
    }

    private fun construct12CharToken(base58: String): String {
        return if (base58.length >= 12) {
            base58.take(12)
        } else {
            base58.padEnd(12, ALPHABET.random(Random(System.currentTimeMillis())))
        }
    }

    private fun encodeBase58(input: ByteArray): String {
        if (input.isEmpty()) return ""
        
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) {
            zeros++
        }
        
        val inputCopy = input.copyOf()
        val temp = CharArray(inputCopy.size * 2) // Pessimistic max size
        var outputStart = temp.size
        
        var inputStart = zeros
        while (inputStart < inputCopy.size) {
            outputStart--
            temp[outputStart] = ALPHABET[divMod(inputCopy, inputStart, 256, 58)]
            if (inputCopy[inputStart].toInt() == 0) {
                inputStart++
            }
        }
        
        while (outputStart < temp.size && temp[outputStart] == '1') {
            outputStart++
        }
        
        while (zeros-- > 0) {
            outputStart--
            temp[outputStart] = '1'
        }
        
        return String(temp, outputStart, temp.size - outputStart)
    }

    private fun divMod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Int {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder
    }
}
