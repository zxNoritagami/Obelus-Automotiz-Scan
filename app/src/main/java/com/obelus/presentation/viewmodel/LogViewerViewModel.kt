package com.obelus.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.canlog.ImportState
import com.obelus.data.canlog.LogRepository
import com.obelus.data.local.entity.CanFrameEntity
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

/**
 * Estado consolidado del Log Viewer.
 */
data class LogViewerUiState(
    val sessionId: String?          = null,
    val totalFrames: Int            = 0,
    val visibleFrames: List<CanFrameEntity> = emptyList(),
    val dbcLoaded: Boolean          = false,
    val dbcDatabase: Map<Int, DbcMessage> = emptyMap(),
    val filterMinId: Int            = 0x000,
    val filterMaxId: Int            = 0x7FF,
    val isLoading: Boolean          = false,
    val errorMessage: String?       = null,
    val importedSessions: List<String> = emptyList()
)

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val logRepository: LogRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object { private const val TAG = "LogViewerViewModel" }

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    val importState: StateFlow<ImportState> = logRepository.importState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImportState.Idle)

    // Track del sessionId activo
    private val _activeSessionId = MutableStateFlow<String?>(null)

    // Frame stream reactivo para la sesión activa
    @OptIn(ExperimentalCoroutinesApi::class)
    val framesFlow = _activeSessionId
        .flatMapLatest { sid ->
            if (sid != null) logRepository.getFramesForSession(sid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Reaccionar al importState para actualizar UI
        viewModelScope.launch {
            logRepository.importState.collect { state ->
                when (state) {
                    is ImportState.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                    }
                    is ImportState.Success -> {
                        _activeSessionId.value = state.sessionId
                        _uiState.value = _uiState.value.copy(
                            sessionId   = state.sessionId,
                            totalFrames = state.frameCount,
                            isLoading   = false,
                            errorMessage = null
                        )
                        loadPage(0) // Carga primera página
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

        // Refrescar sesiones al iniciar
        viewModelScope.launch { refreshSessions() }
    }

    // =========================================================================
    // IMPORTACIÓN
    // =========================================================================

    /**
     * Importa un log CAN desde URI (abierto con SAF/FileManager).
     * @param fileUri URI obtenido del ActivityResultLauncher (ACTION_OPEN_DOCUMENT)
     * @param fileName Nombre del archivo para metadatos
     */
    fun importLog(fileUri: Uri, fileName: String = "log") {
        viewModelScope.launch {
            logRepository.importLog(context, fileUri, fileName)
        }
    }

    /**
     * Carga un archivo .dbc para decodificación de señales.
     */
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

    /**
     * Selecciona una sesión previamente importada.
     */
    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
        _uiState.value = _uiState.value.copy(sessionId = sessionId)
        viewModelScope.launch {
            val count = logRepository.countFrames(sessionId)
            _uiState.value = _uiState.value.copy(totalFrames = count)
            loadPage(0)
        }
    }

    // =========================================================================
    // PAGINACIÓN Y FILTROS
    // =========================================================================

    private var currentOffset = 0
    private val pageSize = 200

    /**
     * Carga una página de frames aplicando filtros de ID.
     * @param offset Offset de inicio (0 = primera página)
     */
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

    /**
     * Aplica filtro por rango de CAN IDs.
     * @param minId CAN ID mínimo (hex string como "000")
     * @param maxId CAN ID máximo (hex string como "7FF")
     */
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
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private suspend fun refreshSessions() {
        val sessions = logRepository.getAllSessionIds()
        _uiState.value = _uiState.value.copy(importedSessions = sessions)
    }

    /**
     * Decodifica las señales DBC para un frame dado.
     * @return Mapa nombre → valor formateado, o vacío si no hay DBC
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
