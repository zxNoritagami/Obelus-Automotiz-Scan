package com.obelus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.local.dao.RaceRecordDao
import com.obelus.data.local.entity.RaceRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RaceHistoryViewModel @Inject constructor(
    private val raceRecordDao: RaceRecordDao
) : ViewModel() {

    private val _records = MutableStateFlow<List<RaceRecord>>(emptyList())
    val records: StateFlow<List<RaceRecord>> = _records.asStateFlow()

    /**
     * Map of raceType → personal best RaceRecord.
     * Used to compute delta for each history item.
     */
    private val _bestByType = MutableStateFlow<Map<String, RaceRecord>>(emptyMap())
    val bestByType: StateFlow<Map<String, RaceRecord>> = _bestByType.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = raceRecordDao.getAll()
            _records.value = all

            // Compute best per type from all records
            val bestMap = mutableMapOf<String, RaceRecord>()
            for (record in all) {
                val existing = bestMap[record.raceType]
                if (existing == null || record.finalTimeSeconds < existing.finalTimeSeconds) {
                    bestMap[record.raceType] = record
                }
            }
            _bestByType.value = bestMap
        }
    }
}
