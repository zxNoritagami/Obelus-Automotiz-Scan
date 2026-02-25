package com.obelus.ui.ddt4all

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.local.entity.ddt4all.Ddt4allEcu
import com.obelus.data.protocol.ddt4all.Ddt4allXmlParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class Ddt4allViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val xmlParser = Ddt4allXmlParser()

    private val _ecuList = MutableStateFlow<List<Ddt4allEcu>>(emptyList())
    val ecuList: StateFlow<List<Ddt4allEcu>> = _ecuList.asStateFlow()

    private val _selectedEcu = MutableStateFlow<Ddt4allEcu?>(null)
    val selectedEcu: StateFlow<Ddt4allEcu?> = _selectedEcu.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadOfflineXmls()
    }

    private fun loadOfflineXmls() {
        viewModelScope.launch {
            _isLoading.value = true
            val ecus = mutableListOf<Ddt4allEcu>()

            withContext(Dispatchers.IO) {
                try {
                    val assetManager = context.assets
                    val files = assetManager.list("ddt4all") ?: emptyArray()

                    for (file in files) {
                        if (file.endsWith(".xml", ignoreCase = true)) {
                            val inputStream: InputStream = assetManager.open("ddt4all/$file")
                            val xmlContent = inputStream.bufferedReader().use { it.readText() }
                            val parsedEcu = xmlParser.parseEcuFile(xmlContent, file)
                            ecus.add(parsedEcu)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _ecuList.value = ecus
            _isLoading.value = false
        }
    }

    fun selectEcu(ecu: Ddt4allEcu) {
        _selectedEcu.value = ecu
    }

    fun clearSelectedEcu() {
        _selectedEcu.value = null
    }
}
