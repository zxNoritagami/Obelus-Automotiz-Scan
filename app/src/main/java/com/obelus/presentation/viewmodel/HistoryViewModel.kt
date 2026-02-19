package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.local.dao.ScanSessionDao
import com.obelus.data.local.entity.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionDao: ScanSessionDao
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<ScanSession>>(emptyList())
    val sessions: StateFlow<List<ScanSession>> = _sessions.asStateFlow()

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _sessions.value = sessionDao.getAll()
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            sessionDao.delete(sessionId)
            loadSessions()
        }
    }
}
