package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.local.dao.RaceRecordDao
import com.obelus.data.local.dao.ScanSessionDao
import com.obelus.data.local.entity.RaceRecord
import com.obelus.data.local.entity.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionDao: ScanSessionDao,
    private val raceRecordDao: RaceRecordDao
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<ScanSession>>(emptyList())
    val sessions: StateFlow<List<ScanSession>> = _sessions.asStateFlow()

    private val _races = MutableStateFlow<List<RaceRecord>>(emptyList())
    val races: StateFlow<List<RaceRecord>> = _races.asStateFlow()

    init {
        loadSessions()
        loadRaces()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _sessions.value = sessionDao.getAll()
        }
    }

    private fun loadRaces() {
        viewModelScope.launch {
            _races.value = raceRecordDao.getAll()
        }
    }

    suspend fun getRace(id: Long): RaceRecord? {
        return raceRecordDao.getById(id)
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            sessionDao.delete(sessionId)
            loadSessions()
        }
    }

    fun deleteRace(raceId: Long) {
        viewModelScope.launch {
            raceRecordDao.deleteById(raceId)
            loadRaces()
        }
    }
}
