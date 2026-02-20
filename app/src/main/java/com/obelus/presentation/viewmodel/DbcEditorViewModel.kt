package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.local.dao.DbcDefinitionDao
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.local.entity.DbcDefinition
import com.obelus.data.local.entity.DbcSignalOverride
import com.obelus.data.local.model.Endian
import com.obelus.data.local.model.SignalSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI State
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
    private val dbcDao: DbcDefinitionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DbcEditorUiState())
    val uiState: StateFlow<DbcEditorUiState> = _uiState.asStateFlow()

    init {
        loadDefinitions()
    }

    // ── List operations ───────────────────────────────────────────────────────

    fun loadDefinitions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val defs = dbcDao.getAll()
                _uiState.value = _uiState.value.copy(
                    definitions = defs,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error cargando definiciones: ${e.message}"
                )
            }
        }
    }

    fun selectDefinition(definition: DbcDefinition) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, selectedDefinition = definition)
            try {
                val signals = dbcDao.getSignalsForDefinition(definition.id)
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
                loadDefinitions()
                // Auto-select the newly created definition
                val created = dbcDao.getById(id)
                if (created != null) selectDefinition(created)
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
                _uiState.value = _uiState.value.copy(selectedDefinition = updated)
                loadDefinitions()
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
                if (_uiState.value.selectedDefinition?.id == definition.id) {
                    _uiState.value = _uiState.value.copy(
                        selectedDefinition = null,
                        selectedSignals = emptyList()
                    )
                }
                loadDefinitions()
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
                // Refresh signal count cache
                val count = dbcDao.getSignalsForDefinition(defId).size
                dbcDao.updateSignalCount(defId, count)
                // Reload signals for selected definition
                val signals = dbcDao.getSignalsForDefinition(defId)
                _uiState.value = _uiState.value.copy(selectedSignals = signals)
                loadDefinitions()
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
                // Remove from list (DAO doesn't have a direct delete-by-id for signals,
                // so we update the signal setting dbcDefinitionId = null via canSignalDao;
                // here we just reload after the operation — using the unlink approach)
                val updated = signal.copy(dbcDefinitionId = null)
                dbcDao.insertSignals(listOf(updated)) // REPLACE strategy will update
                val signals = dbcDao.getSignalsForDefinition(defId)
                val count = signals.size
                dbcDao.updateSignalCount(defId, count)
                _uiState.value = _uiState.value.copy(selectedSignals = signals)
                loadDefinitions()
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
