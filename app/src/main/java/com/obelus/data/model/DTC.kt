package com.obelus.data.model

data class DTC(
    val code: String,           // Ej: "P0133"
    val category: DTCCategory,  // POWERTRAIN, CHASSIS, BODY, NETWORK
    val description: String,    // Descripción en español
    val severity: DTCSeverity,  // LOW, MEDIUM, HIGH, CRITICAL
    val isPermanent: Boolean = false
)

enum class DTCCategory { POWERTRAIN, CHASSIS, BODY, NETWORK }

enum class DTCSeverity { LOW, MEDIUM, HIGH, CRITICAL }

// Mapeo básico de códigos comunes
val COMMON_DTCS = mapOf(
    "P0100" to "Problema en sensor MAF (Flujo de Masa de Aire)",
    "P0101" to "Problema en rango/rendimiento del sensor MAF",
    "P0102" to "Entrada baja en circuito de sensor MAF",
    "P0103" to "Entrada alta en circuito de sensor MAF",
    "P0113" to "Entrada alta en sensor de temperatura de aire de admisión (IAT)",
    "P0115" to "Mal funcionamiento de sensor de temperatura del refrigerante (ECT)",
    "P0120" to "Mal funcionamiento en circuito del sensor de posición del acelerador (TPS)",
    "P0130" to "Mal funcionamiento en circuito del sensor de O2 (Banco 1 Sensor 1)",
    "P0133" to "Sensor de oxígeno (O2) lento en respuesta (Banco 1 Sensor 1)",
    "P0134" to "Actividad no detectada en sensor de O2 (Banco 1 Sensor 1)",
    "P0171" to "Sistema demasiado pobre (Banco 1)",
    "P0172" to "Sistema demasiado rico (Banco 1)",
    "P0200" to "Mal funcionamiento en circuito de inyectores",
    "P0300" to "Fallo de encendido aleatorio/múltiple detectado",
    "P0301" to "Fallo de encendido detectado en cilindro 1",
    "P0302" to "Fallo de encendido detectado en cilindro 2",
    "P0303" to "Fallo de encendido detectado en cilindro 3",
    "P0304" to "Fallo de encendido detectado en cilindro 4",
    "P0420" to "Eficiencia del sistema de catalizador por debajo del umbral (Banco 1)",
    "P0440" to "Mal funcionamiento en sistema de control de emisiones evaporativas (EVAP)",
    "P0500" to "Mal funcionamiento en sensor de velocidad del vehículo (VSS)",
    "P0600" to "Enlace de comunicación serial dañado",
    "P0700" to "Mal funcionamiento en sistema de control de transmisión"
)
