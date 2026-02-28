package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.sniffer.CanSnifferEngine
import com.obelus.domain.model.CanFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SnifferViewModel @Inject constructor(
    private val snifferEngine: CanSnifferEngine
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isFrozen = MutableStateFlow(false)
    val isFrozen = _isFrozen.asStateFlow()

    val filteredFrames: StateFlow<List<CanFrame>> = combine(
        snifferEngine.activeFrames,
        _searchQuery
    ) { frames, query ->
        frames.values
            .filter { it.id.contains(query, ignoreCase = true) }
            .sortedByDescending { it.lastChanged }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startSniffing() {
        snifferEngine.startSniffing()
    }

    fun stopSniffing() {
        snifferEngine.stopSniffing()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleFreeze() {
        _isFrozen.value = !_isFrozen.value
        snifferEngine.toggleFreeze()
    }
}
