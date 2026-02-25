package com.obelus.data.protocol.actuator

import com.obelus.domain.model.ActuatorTest
import com.obelus.domain.model.TestResult
import com.obelus.protocol.ElmConnection
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the actuator test subsystem.
 *
 * Implementations must:
 * - Provide a catalogue of supported tests for the given connection.
 * - Stream [TestResult] events during execution (progress → success/failure/timeout).
 * - Handle timeouts and bad ECU responses gracefully without crashing.
 */
interface ActuatorTestRepository {

    /**
     * Returns the list of actuator tests available.
     * The list is static and hardware-independent — filtering by capability
     * (e.g. protocol version) can be done at the ViewModel layer if needed.
     */
    fun getAvailableTests(): List<ActuatorTest>

    /**
     * Executes the test identified by [testId], communicating via [connection].
     * Emits [TestResult.Progress] during execution, then a terminal
     * [TestResult.Success], [TestResult.Failure], or [TestResult.Timeout].
     *
     * @param testId Must match [ActuatorTest.id] from [getAvailableTests].
     * @param connection Active OBD2/ELM327 connection.
     */
    fun executeTest(
        testId: String,
        connection: ElmConnection
    ): Flow<TestResult>

    /** Signals the current test execution should be cancelled. */
    fun stopCurrentTest()
}
