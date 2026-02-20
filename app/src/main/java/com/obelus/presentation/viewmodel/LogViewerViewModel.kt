package com.obelus.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.canlog.DecodingState
import com.obelus.data.canlog.ImportState
import com.obelus.data.canlog.LogRepository
import com.obelus.data.local.dao.DbcDefinitionDao
import com.obelus.data.local.entity.CanFrameEntity
import com.obelus.data.local.entity.DbcDefinition
import com.obelus.data.local.entity.DecodedSignal
import com.obelus.data.protocol.DbcMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// View mode enum
// ─────────────────────────────────────────────────────────────────────────────

enum class ViewMode { RAW, DECODED }

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

data class LogViewerUiState(
    // Session / frames
    val sessionId: String?                   = null,
    val totalFrames: Int                     = 0,
    val visibleFrames: List<CanFrameEntity>  = emptyList(),
    val importedSessions: List<String>       = emptyList(),

    // Legacy file-based DBC
    val dbcLoaded: Boolean                   = false,
    val dbcDatabase: Map<Int, DbcMessage>    = emptyMap(),

    // Room DBC integration
    val availableDbcDefinitions: List<DbcDefinition> = emptyList(),
    val appliedDbcDefinition: DbcDefinition? = null,
    val viewMode: ViewMode                   = ViewMode.RAW,

    // Filters
    val filterMinId: Int                     = 0x000,
    val filterMaxId: Int                     = 0x1FFFFFFF,

    // Status
    val isLoading: Boolean                   = false,
    val errorMessage: String?                = null,
    val decodingProgress: Float?             = null  // null = idle, 0..1 = in progress
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val logRepository: LogRepository,
    private val dbcDefinitionDao: DbcDefinitionDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object { private const val TAG = "LogViewerViewModel" }

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    val importState: StateFlow<ImportState> = logRepository.importState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImportState.Idle)

    val decodingState: StateFlow<DecodingState> = logRepository.decodingState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DecodingState.Idle)

    // Frame stream reactivo para la sesión activa
    private val _activeSessionId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val framesFlow = _activeSessionId
        .flatMapLatest { sid ->
            if (sid != null) logRepository.getFramesForSession(sid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Decoded signals stream (latest per signal for summary cards)
    private val _decodedSignals = MutableStateFlow<List<DecodedSignal>>(emptyList())
    val decodedSignals: StateFlow<List<DecodedSignal>> = _decodedSignals.asStateFlow()

    init {
        // React to import state
        viewModelScope.launch {
            logRepository.importState.collect { state ->
                when (state) {
                    is ImportState.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                    }
                    is ImportState.Success -> {
                        _activeSessionId.value = state.sessionId
                        _uiState.value = _uiState.value.copy(
                            sessionId    = state.sessionId,
                            totalFrames  = state.frameCount,
                            isLoading    = false,
                            errorMessage = null,
                            // Reset DBC state for new session
                            appliedDbcDefinition = null,
                            viewMode     = ViewMode.RAW
                        )
                        loadPage(0)
                        refreshSessions()
                    }
                    is ImportState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading    = false,
                            errorMessage = state.message
                        )
                    }
                    ImportState.Idle -> {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
            }
        }

        // React to decoding state
        viewModelScope.launch {
            logRepository.decodingState.collect { state ->
                when (state) {
                    is DecodingState.Processing -> {
                        _uiState.value = _uiState.value.copy(decodingProgress = state.progress)
                    }
                    is DecodingState.Done -> {
                        _uiState.value = _uiState.value.copy(decodingProgress = null)
                        // Switch to decoded view automatically
                        _uiState.value = _uiState.value.copy(viewMode = ViewMode.DECODED)
                        // Reload decoded signals
                        loadDecodedSignals()
                    }
                    is DecodingState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            decodingProgress = null,
                            errorMessage     = state.message
                        )
                    }
                    DecodingState.Idle -> {
                        _uiState.value = _uiState.value.copy(decodingProgress = null)
                    }
                }
            }
        }

        viewModelScope.launch { refreshSessions() }
        viewModelScope.launch { loadAvailableDbcDefinitions() }
    }

    // =========================================================================
    // IMPORTACIÓN
    // =========================================================================

    fun importLog(fileUri: Uri, fileName: String = "log") {
        viewModelScope.launch {
            logRepository.importLog(context, fileUri, fileName)
        }
    }

    /** Carga un archivo .dbc para decodificación de señales (flujo legacy). */
    fun loadDbc(fileUri: Uri) {
        viewModelScope.launch {
            val success = logRepository.loadDbc(context, fileUri)
            _uiState.value = _uiState.value.copy(
                dbcLoaded   = success,
                dbcDatabase = if (success) logRepository.dbcDatabase else emptyMap()
            )
            Log.i(TAG, if (success) "DBC cargado ✅" else "Error cargando DBC ❌")
        }
    }

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
        _uiState.value = _uiState.value.copy(
            sessionId            = sessionId,
            appliedDbcDefinition = null,
            viewMode             = ViewMode.RAW
        )
        viewModelScope.launch {
            val count = logRepository.countFrames(sessionId)
            _uiState.value = _uiState.value.copy(totalFrames = count)
            loadPage(0)
        }
    }

    // =========================================================================
    // DBC ROOM INTEGRATION
    // =========================================================================

    /** Loads all DBC definitions available in Room. */
    fun loadAvailableDbcDefinitions() {
        viewModelScope.launch {
            try {
                val defs = dbcDefinitionDao.getAll()
                _uiState.value = _uiState.value.copy(availableDbcDefinitions = defs)
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando definiciones DBC: ${e.message}")
            }
        }
    }

    /**
     * Applies a DBC definition from Room to the current session.
     * Triggers the full decoding pipeline in LogRepository.
     */
    fun applyDbcDefinition(definition: DbcDefinition) {
        val sid = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            try {
                val signals = dbcDefinitionDao.getSignalsForDefinition(definition.id)
                _uiState.value = _uiState.value.copy(appliedDbcDefinition = definition)
                logRepository.applyDbcDefinition(sid, definition, signals)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al aplicar DBC: ${e.message}"
                )
                Log.e(TAG, "applyDbcDefinition error", e)
            }
        }
    }

    /** Fetches the latest decoded value per signal for the applied DBC. */
    fun loadDecodedSignals() {
        val sid    = _uiState.value.sessionId ?: return
        val dbcDef = _uiState.value.appliedDbcDefinition ?: return
        viewModelScope.launch {
            logRepository.getLatestDecodedPerSignal(sid, dbcDef.id).collect { signals ->
                _decodedSignals.value = signals
            }
        }
    }

    /** Returns all historical values for a signal (used in detail chart dialog). */
    suspend fun getSignalHistory(signalId: Long): List<DecodedSignal> {
        val sid = _uiState.value.sessionId ?: return emptyList()
        return logRepository.getSignalHistory(signalId, sid)
    }

    fun toggleViewMode() {
        val current = _uiState.value.viewMode
        val next = if (current == ViewMode.RAW) ViewMode.DECODED else ViewMode.RAW
        _uiState.value = _uiState.value.copy(viewMode = next)
        if (next == ViewMode.DECODED) loadDecodedSignals()
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
        if (mode == ViewMode.DECODED) loadDecodedSignals()
    }

    // =========================================================================
    // PAGINACIÓN Y FILTROS
    // =========================================================================

    private var currentOffset = 0
    private val pageSize = 200

    fun loadPage(offset: Int) {
        val sid = _activeSessionId.value ?: return
        viewModelScope.launch {
            val frames = logRepository.getFramesPaged(
                sessionId = sid,
                minId     = _uiState.value.filterMinId,
                maxId     = _uiState.value.filterMaxId,
                limit     = pageSize,
                offset    = offset
            )
            currentOffset = offset
            _uiState.value = _uiState.value.copy(visibleFrames = frames)
        }
    }

    fun nextPage() = loadPage(currentOffset + pageSize)
    fun prevPage() = loadPage((currentOffset - pageSize).coerceAtLeast(0))

    fun applyIdFilter(minIdHex: String, maxIdHex: String) {
        val min = minIdHex.toIntOrNull(16) ?: 0x000
        val max = maxIdHex.toIntOrNull(16) ?: 0x7FF
        _uiState.value = _uiState.value.copy(filterMinId = min, filterMaxId = max)
        loadPage(0)
    }

    fun clearIdFilter() {
        _uiState.value = _uiState.value.copy(filterMinId = 0, filterMaxId = 0x1FFFFFFF)
        loadPage(0)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        logRepository.resetImportState()
        logRepository.resetDecodingState()
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private suspend fun refreshSessions() {
        val sessions = logRepository.getAllSessionIds()
        _uiState.value = _uiState.value.copy(importedSessions = sessions)
    }

    /**
     * Decodes signals for a frame using the legacy file-based DBC (DbcParser).
     * Used only when a .dbc file has been loaded manually.
     */
    fun decodeSignals(frame: CanFrameEntity): Map<String, String> {
        val db = _uiState.value.dbcDatabase
        if (db.isEmpty()) return emptyMap()
        val msg = db[frame.canId] ?: return emptyMap()
        val data = frame.dataHex.replace(" ", "")
            .chunked(2).map { it.toIntOrNull(16)?.toByte() ?: 0 }.toByteArray()
        return msg.signals.associate { sig ->
            sig.name to sig.formatValue(data)
        }
    }
}
