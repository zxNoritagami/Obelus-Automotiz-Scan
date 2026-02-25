package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.local.dao.DbcDefinitionDao
import com.obelus.data.crash.CrashReporter
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.local.entity.DbcDefinition
import com.obelus.data.local.entity.DbcSignalOverride
import com.obelus.data.local.model.Endian
import com.obelus.data.local.model.SignalSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI State — single consolidated StateFlow
// ─────────────────────────────────────────────────────────────────────────────

data class DbcEditorUiState(
    val definitions: List<DbcDefinition> = emptyList(),
    val selectedDefinition: DbcDefinition? = null,
    val selectedSignals: List<CanSignal> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showCreateDialog: Boolean = false,
    val showSignalDialog: Boolean = false,
    val editingSignal: CanSignal? = null   // null = new signal, non-null = edit existing
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class DbcEditorViewModel @Inject constructor(
    private val dbcDao: DbcDefinitionDao,
    private val crashReporter: CrashReporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(DbcEditorUiState())
    val uiState: StateFlow<DbcEditorUiState> = _uiState.asStateFlow()

    init {
        // Defer initial load to avoid emitting state during the init block
        viewModelScope.launch {
            loadDefinitions()
        }
    }

    // ── List operations ───────────────────────────────────────────────────────

    fun loadDefinitions() {
        viewModelScope.launch {
            // Batch: set loading=true in one emission
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val defs = dbcDao.getAll()
                // Batch: set definitions + loading=false in one single emission
                _uiState.value = _uiState.value.copy(
                    definitions = defs,
                    isLoading = false
                )
            } catch (e: Exception) {
                crashReporter.logCrash(e, "DbcEditor_loadDefinitions")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error cargando definiciones: ${e.message}"
                )
            }
        }
    }

    fun selectDefinition(definition: DbcDefinition) {
        viewModelScope.launch {
            // Batch: set selectedDefinition + isLoading in one emission
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedDefinition = definition,
                selectedSignals = emptyList()   // Clear stale data immediately
            )
            try {
                val signals = dbcDao.getSignalsForDefinition(definition.id)
                // Batch: set signals + loading=false in one single emission
                _uiState.value = _uiState.value.copy(
                    selectedSignals = signals,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error cargando señales: ${e.message}"
                )
            }
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedDefinition = null,
            selectedSignals = emptyList()
        )
    }

    // ── Definition CRUD ───────────────────────────────────────────────────────

    fun createDefinition(name: String, description: String?, protocol: String) {
        viewModelScope.launch {
            try {
                val newDef = DbcDefinition(
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    protocol = protocol,
                    isBuiltIn = false
                )
                val id = dbcDao.insertDefinition(newDef)
                val defs = dbcDao.getAll()
                val created = dbcDao.getById(id)
                val signals = if (created != null) {
                    dbcDao.getSignalsForDefinition(created.id)
                } else emptyList()

                // All updates in ONE single emission — no rapid sequential mutations
                _uiState.value = _uiState.value.copy(
                    definitions = defs,
                    selectedDefinition = created,
                    selectedSignals = signals,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error creando definición: ${e.message}"
                )
            }
        }
    }

    fun updateDefinition(name: String, description: String?, protocol: String) {
        val current = _uiState.value.selectedDefinition ?: return
        if (current.isBuiltIn) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Las definiciones integradas no se pueden editar"
            )
            return
        }
        viewModelScope.launch {
            try {
                val updated = current.copy(
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    protocol = protocol,
                    updatedAt = System.currentTimeMillis()
                )
                dbcDao.updateDefinition(updated)
                val defs = dbcDao.getAll()
                // ONE emission: update definitions list + selectedDefinition
                _uiState.value = _uiState.value.copy(
                    definitions = defs,
                    selectedDefinition = updated
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error actualizando definición: ${e.message}"
                )
            }
        }
    }

    fun deleteDefinition(definition: DbcDefinition) {
        if (definition.isBuiltIn) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Las definiciones integradas no se pueden eliminar"
            )
            return
        }
        viewModelScope.launch {
            try {
                dbcDao.deleteUserDefinition(definition.id)
                val defs = dbcDao.getAll()
                val wasSelected = _uiState.value.selectedDefinition?.id == definition.id
                // ONE emission: clear selection if needed, update list
                _uiState.value = _uiState.value.copy(
                    definitions = defs,
                    selectedDefinition = if (wasSelected) null else _uiState.value.selectedDefinition,
                    selectedSignals = if (wasSelected) emptyList() else _uiState.value.selectedSignals
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error eliminando definición: ${e.message}"
                )
            }
        }
    }

    // ── Signal CRUD ───────────────────────────────────────────────────────────

    fun createCustomSignal(
        name: String,
        canId: String,
        startBit: Int,
        length: Int,
        factor: Float,
        offset: Float,
        unit: String,
        endian: Endian,
        signed: Boolean
    ) {
        val defId = _uiState.value.selectedDefinition?.id ?: return
        viewModelScope.launch {
            try {
                val signal = CanSignal(
                    name = name.trim(),
                    description = null,
                    canId = canId.trim().uppercase(),
                    isExtended = false,
                    startByte = startBit / 8,
                    startBit = startBit,
                    bitLength = length,
                    endianness = endian,
                    scale = factor,
                    offset = offset,
                    signed = signed,
                    unit = unit.trim().takeIf { it.isNotEmpty() },
                    minValue = null,
                    maxValue = null,
                    source = SignalSource.MANUAL,
                    sourceFile = null,
                    category = null,
                    dbcDefinitionId = defId,
                    isCustom = true
                )
                dbcDao.insertSignals(listOf(signal))
                val signals = dbcDao.getSignalsForDefinition(defId)
                dbcDao.updateSignalCount(defId, signals.size)
                val defs = dbcDao.getAll()
                // ONE emission with everything updated
                _uiState.value = _uiState.value.copy(
                    selectedSignals = signals,
                    definitions = defs
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error creando señal: ${e.message}"
                )
            }
        }
    }

    fun deleteSignal(signal: CanSignal) {
        val defId = _uiState.value.selectedDefinition?.id ?: return
        viewModelScope.launch {
            try {
                val updated = signal.copy(dbcDefinitionId = null)
                dbcDao.insertSignals(listOf(updated)) // REPLACE strategy will update
                val signals = dbcDao.getSignalsForDefinition(defId)
                dbcDao.updateSignalCount(defId, signals.size)
                val defs = dbcDao.getAll()
                // ONE emission
                _uiState.value = _uiState.value.copy(
                    selectedSignals = signals,
                    definitions = defs
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error eliminando señal: ${e.message}"
                )
            }
        }
    }

    fun updateSignalOverride(
        signalId: Long,
        customName: String?,
        notes: String?
    ) {
        val defId = _uiState.value.selectedDefinition?.id ?: return
        viewModelScope.launch {
            try {
                val override = DbcSignalOverride(
                    dbcDefinitionId = defId,
                    canSignalId = signalId,
                    nameOverride = customName?.trim()?.takeIf { it.isNotEmpty() },
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() }
                )
                dbcDao.upsertOverride(override)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error actualizando override: ${e.message}"
                )
            }
        }
    }

    // ── Dialog helpers ────────────────────────────────────────────────────────

    fun showCreateDefinitionDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun showSignalEditorDialog(signal: CanSignal? = null) {
        _uiState.value = _uiState.value.copy(showSignalDialog = true, editingSignal = signal)
    }

    fun dismissSignalDialog() {
        _uiState.value = _uiState.value.copy(showSignalDialog = false, editingSignal = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
