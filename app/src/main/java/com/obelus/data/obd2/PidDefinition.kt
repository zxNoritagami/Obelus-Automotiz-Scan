package com.obelus.data.obd2

data class PidDefinition(
    val pid: String,           // Código hex (ej: "0C")
    val name: String,          // Nombre humano (ej: "Engine RPM")
    val description: String,   // Descripción técnica
    val unit: String,          // Unidad (ej: "rpm", "km/h", "°C")
    val minValue: Float,       // Valor mínimo posible
    val maxValue: Float,       // Valor máximo posible
    val decoder: (ByteArray) -> Float  // Función de decodificación
)
