package com.obelus.obelusscan.domain.model

import java.util.UUID

/**
 * Representa un intervalo de tiempo para un rango de velocidad específico.
 */
data class SplitTime(
    val speedFrom: Int,
    val speedTo: Int,
    val timeMs: Long
)

/**
 * Tipos de carrera o prueba de rendimiento soportados.
 */
enum class RaceType {
    ACCELERATION_0_100, // 0-100 km/h
    ACCELERATION_0_200, // 0-200 km/h (Requiere pista cerrada)
    BRAKING_100_0,      // 100-0 km/h (Frenada)
    CUSTOM              // Rango definido por usuario
}

/**
 * Estados posibles de la máquina de estados de la carrera.
 */
enum class RaceState {
    IDLE,       // Esperando configuración o inicio
    ARMED,      // Listo para arrancar (esperando movimiento > 0km/h)
    COUNTDOWN,  // (Opcional) Cuenta atrás visual
    RUNNING,    // Carrera en curso, midiendo tiempo
    FINISHED,   // Objetivo alcanzado, mostrando resultados
    ERROR       // Cancelado o error de lectura
}

/**
 * Modelo de datos inmutable para una sesión de carrera.
 */
data class RaceSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val type: RaceType,
    val targetSpeedStart: Int,
    val targetSpeedEnd: Int,
    
    // Resultados dinámicos
    val times: List<SplitTime> = emptyList(),
    val finalTime: Float = 0f, // Segundos
    val maxGforce: Float = 0f,
    val completed: Boolean = false
) {
    init {
        // Validación básica de integridad
        if (type != RaceType.BRAKING_100_0 && targetSpeedEnd <= targetSpeedStart) {
            // Para aceleración, end > start
            throw IllegalArgumentException("Target speed end ($targetSpeedEnd) must be greater than start ($targetSpeedStart) for acceleration.")
        }
        if (type == RaceType.BRAKING_100_0 && targetSpeedEnd >= targetSpeedStart) {
            // Para frenada, end < start
            throw IllegalArgumentException("Target speed end ($targetSpeedEnd) must be less than start ($targetSpeedStart) for braking.")
        }
    }
}
