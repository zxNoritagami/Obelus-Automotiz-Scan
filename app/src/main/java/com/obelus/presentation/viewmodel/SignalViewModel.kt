package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.repository.ObelusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignalViewModel @Inject constructor(
    private val repository: ObelusRepository
) : ViewModel() {

    private val _allSignals = MutableStateFlow<List<CanSignal>>(emptyList())
    val allSignals: StateFlow<List<CanSignal>> = _allSignals.asStateFlow()

    private val _favoriteSignals = MutableStateFlow<List<CanSignal>>(emptyList())
    val favoriteSignals: StateFlow<List<CanSignal>> = _favoriteSignals.asStateFlow()

    init {
        loadSignals()
    }

    private fun loadSignals() {
        viewModelScope.launch {
            _allSignals.value = repository.getSignals()
            // In a real implementation, getFavorites() might be a separate DAO call 
            // or filtered from getAll(). Assuming repository has a way or we filter here:
            _favoriteSignals.value = _allSignals.value.filter { it.isFavorite }
        }
    }

    fun getAllSignals() {
        loadSignals()
    }

    fun getFavoriteSignals() {
        // Refresh favorites
        viewModelScope.launch {
             _favoriteSignals.value = _allSignals.value.filter { it.isFavorite }
        }
    }

    fun searchSignals(query: String) {
        viewModelScope.launch {
            _allSignals.value = if (query.isBlank()) {
                repository.getSignals()
            } else {
                 // Assuming repository/DAO has searchByName(query)
                // For this example using local filter or assume repository.getSignals() returns all
                repository.getSignals().filter { it.name.contains(query, ignoreCase = true) }
            }
        }
    }

    fun addSignal(signal: CanSignal) {
        viewModelScope.launch {
            repository.addSignal(signal)
            loadSignals()
        }
    }

    fun toggleFavorite(signalId: Long) {
        viewModelScope.launch {
            // Find current state
            val signal = _allSignals.value.find { it.id == signalId }
            if (signal != null) {
                repository.toggleFavorite(signalId, !signal.isFavorite)
                loadSignals()
            }
        }
    }
}
