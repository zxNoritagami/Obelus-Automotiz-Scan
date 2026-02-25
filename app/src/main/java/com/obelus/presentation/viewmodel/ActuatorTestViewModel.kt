package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.protocol.actuator.ActuatorTestRepository
import com.obelus.domain.model.ActuatorCategory
import com.obelus.domain.model.ActuatorTest
import com.obelus.domain.model.TestResult
import com.obelus.protocol.ElmConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lifecycle state of a test run. */
enum class TestExecutionState { IDLE, RUNNING, SUCCESS, FAILED, ERROR }

data class ActuatorUiState(
    val availableTests: List<ActuatorTest> = emptyList(),
    val testsByCategory: Map<ActuatorCategory, List<ActuatorTest>> = emptyMap(),
    val executionState: TestExecutionState = TestExecutionState.IDLE,
    val currentTestId: String? = null,
    val progressMessage: String? = null,
    val elapsedMs: Long = 0L,
    val lastRawResponse: String? = null,
    val lastParsedValue: String? = null,
    val errorMessage: String? = null,
    /** Non-null when a test with safetyWarning is selected but not yet confirmed. */
    val pendingSafetyConfirmation: String? = null,
    val pendingTestId: String? = null
)

@HiltViewModel
class ActuatorTestViewModel @Inject constructor(
    private val repository: ActuatorTestRepository,
    private val elmConnection: ElmConnection   // @Named("bluetooth") bound by Hilt
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActuatorUiState())
    val uiState: StateFlow<ActuatorUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null

    init { loadTests() }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadTests() {
        val tests = repository.getAvailableTests()
        val byCategory = tests.groupBy { it.category }
        _uiState.value = _uiState.value.copy(
            availableTests = tests,
            testsByCategory = byCategory
        )
    }

    /**
     * Initiates a test run.
     * If the test has a [ActuatorTest.safetyWarning], stores it in [ActuatorUiState.pendingSafetyConfirmation]
     * for the UI to display the confirmation dialog before actually executing.
     */
    fun requestTest(testId: String) {
        if (_uiState.value.executionState == TestExecutionState.RUNNING) return

        val test = _uiState.value.availableTests.firstOrNull { it.id == testId } ?: return

        if (test.safetyWarning != null) {
            _uiState.value = _uiState.value.copy(
                pendingSafetyConfirmation = test.safetyWarning,
                pendingTestId = testId
            )
        } else {
            executeTest(testId)
        }
    }

    /** Called when user confirms the safety dialog. */
    fun confirmSafety() {
        val pending = _uiState.value.pendingTestId ?: return
        _uiState.value = _uiState.value.copy(
            pendingSafetyConfirmation = null,
            pendingTestId = null
        )
        executeTest(pending)
    }

    /** Called when user dismisses the safety dialog without confirming. */
    fun dismissSafety() {
        _uiState.value = _uiState.value.copy(
            pendingSafetyConfirmation = null,
            pendingTestId = null
        )
    }

    fun stopTest() {
        repository.stopCurrentTest()
        testJob?.cancel()
        _uiState.value = _uiState.value.copy(
            executionState = TestExecutionState.IDLE,
            progressMessage = null,
            currentTestId = null
        )
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun executeTest(testId: String) {
        testJob?.cancel()

        _uiState.value = _uiState.value.copy(
            executionState = TestExecutionState.RUNNING,
            currentTestId  = testId,
            progressMessage = "Iniciando test...",
            elapsedMs      = 0L,
            lastRawResponse = null,
            lastParsedValue = null,
            errorMessage   = null
        )

        testJob = viewModelScope.launch {
            repository.executeTest(testId, elmConnection).collect { result ->
                when (result) {
                    is TestResult.Progress -> _uiState.value = _uiState.value.copy(
                        progressMessage = result.message,
                        elapsedMs = result.elapsedMs
                    )
                    is TestResult.Success -> _uiState.value = _uiState.value.copy(
                        executionState  = TestExecutionState.SUCCESS,
                        lastRawResponse = result.rawResponse,
                        lastParsedValue = result.parsedValue,
                        elapsedMs       = result.elapsedMs,
                        progressMessage = null,
                        currentTestId   = testId
                    )
                    is TestResult.Failure -> _uiState.value = _uiState.value.copy(
                        executionState  = TestExecutionState.FAILED,
                        errorMessage    = result.reason,
                        lastRawResponse = result.rawResponse,
                        progressMessage = null
                    )
                    is TestResult.Timeout -> _uiState.value = _uiState.value.copy(
                        executionState  = TestExecutionState.ERROR,
                        errorMessage    = "Timeout: el ECU no respondió en 10 segundos",
                        progressMessage = null
                    )
                }
            }
        }
    }
}
