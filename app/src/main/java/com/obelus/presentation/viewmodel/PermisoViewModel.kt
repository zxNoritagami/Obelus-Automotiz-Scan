package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class PermisoEstado {
    CONCEDIDOS,
    DENEGADOS,
    PERMANENTEMENTE_DENEGADOS,
    REQUIERE_RATIONALE
}

@HiltViewModel
class PermisoViewModel @Inject constructor() : ViewModel() {

    private val _estadoPermiso = MutableStateFlow(PermisoEstado.DENEGADOS)
    val estadoPermiso: StateFlow<PermisoEstado> = _estadoPermiso.asStateFlow()

    fun actualizarEstado(nuevoEstado: PermisoEstado) {
        _estadoPermiso.value = nuevoEstado
    }
}
