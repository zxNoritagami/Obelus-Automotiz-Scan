package com.obelus.domain.model

/**
 * Categories that group actuator tests by vehicle system.
 */
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
    val command: String,
    val expectedResponse: String? = null,
    val safetyWarning: String? = null,
    val category: ActuatorCategory
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
