package com.obelus.data.security

object OtpGenerator {
    fun generateOtp(mechanicName: String): String {
        // Validación básica
        val safeName = if (mechanicName.length > 10) mechanicName.take(10) else mechanicName
        val lettersOnly = safeName.filter { it.isLetter() }.ifEmpty { "Mechanic" }
        
        // Construir string base
        val multipliers = listOf(3, 13, 24, 36, 48, 60, 72, 84, 96, 108)
        val base = buildString {
            append(lettersOnly.uppercase())
            lettersOnly.uppercase().take(10).forEachIndexed { index, char ->
                val value = char.code - 'A'.code + 1
                val result = value * multipliers.getOrElse(index) { 108 }
                append(result)
            }
        }
        
        // SHA-256 nativo de Android
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(base.toByteArray())
        
        // Hex truncado simple
        return hashBytes.take(8).joinToString("") { "%02x".format(it) }
    }
}
