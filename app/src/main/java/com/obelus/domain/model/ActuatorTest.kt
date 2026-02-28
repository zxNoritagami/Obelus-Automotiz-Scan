package com.obelus.domain.model

/**
 * Risk level of an actuator test.
 * Used to display color-coded warnings in the UI and trigger sound alerts.
 */
enum class DangerLevel(val label: String, val colorHex: Long) {
    /** Lectura de datos, ningún riesgo de daño. */
    LOW("Bajo",    0xFF4CAF50),   // green
    /** Activa componentes, posible olor/vibración. */
    MEDIUM("Medio", 0xFFFFC107),  // amber
    /** Puede dañar componentes si se realiza incorrectamente. */
    HIGH("Alto",   0xFFF44336)    // red
}
enum class ActuatorCategory(val displayName: String) {
    ENGINE("Motor"),
    FUEL("Combustible"),
    COOLING("Refrigeración"),
    ELECTRICAL("Eléctrico"),
    EMISSIONS("Emisiones")
}

/**
 * Represents a single ECU actuator test that can be executed via OBD2 commands.
 *
 * @param id Unique identifier used internally to dispatch the correct command sequence.
 * @param name Human-readable test name (shown in UI).
 * @param description Short explanation of what the test does.
 * @param command Primary OBD2 command to send (e.g. "0101", "0145").
 * @param expectedResponse Optional prefix to validate ECU response (e.g. "41 01").
 * @param safetyWarning Non-null → user must acknowledge before executing.
 * @param category Groups the test under a system section in the UI.
 */
data class ActuatorTest(
    val id: String,
    val name: String,
    val description: String,
    /** OBD2 command to send (e.g. "010C"). */
    val command: String,
    val expectedResponse: String? = null,
    /** Non-null → user must confirm before executing. */
    val safetyWarning: String? = null,
    val category: ActuatorCategory,
    /**
     * Risk level of this test.
     * Defaults to LOW for read-only diagnostic commands.
     * HIGH tests will also trigger an audio alert on supported devices.
     */
    val dangerLevel: DangerLevel = DangerLevel.LOW
)

/**
 * Result of a single actuator test execution step.
 */
sealed class TestResult {
    data class Progress(val message: String, val elapsedMs: Long) : TestResult()
    data class Success(val rawResponse: String, val parsedValue: String, val elapsedMs: Long) : TestResult()
    data class Failure(val reason: String, val rawResponse: String? = null) : TestResult()
    object Timeout : TestResult()
}
