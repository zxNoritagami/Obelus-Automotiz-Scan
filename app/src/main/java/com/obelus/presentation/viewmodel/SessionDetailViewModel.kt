package com.obelus.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.export.ExportManager
import com.obelus.data.export.ExportProgress
import com.obelus.data.local.dao.DtcCodeDao
import com.obelus.data.local.dao.ScanSessionDao
import com.obelus.data.local.dao.SignalReadingDao
import com.obelus.data.local.entity.DtcCode
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import com.obelus.data.obd2.Obd2Decoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PidStatistics(
    val average: Float,
    val max: Float,
    val min: Float,
    val count: Int
)

sealed class ExportStatus {
    object Idle : ExportStatus()
    object Loading : ExportStatus()
    object Success : ExportStatus()
    data class Error(val message: String) : ExportStatus()
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionDao: ScanSessionDao,
    private val readingDao: SignalReadingDao,
    private val dtcCodeDao: DtcCodeDao,
    private val exportManager: ExportManager
) : ViewModel() {

    val sessionId: Long = savedStateHandle["sessionId"] ?: throw IllegalArgumentException("Session ID required")

    private val _session = MutableStateFlow<ScanSession?>(null)
    val session: StateFlow<ScanSession?> = _session.asStateFlow()

    private val _readings = MutableStateFlow<List<SignalReading>>(emptyList())
    val readings: StateFlow<List<SignalReading>> = _readings.asStateFlow()

    private val _selectedPid = MutableStateFlow("0C") // Default RPM
    val selectedPid: StateFlow<String> = _selectedPid.asStateFlow()

    /** Legacy CSV export status — kept for backwards compat with existing Toast logic */
    private val _exportStatus = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val exportStatus: StateFlow<ExportStatus> = _exportStatus.asStateFlow()

    /** New: progress for PDF/Excel export (drives LinearProgressIndicator in UI) */
    private val _exportProgress = MutableStateFlow<ExportProgress?>(null)
    val exportProgress: StateFlow<ExportProgress?> = _exportProgress.asStateFlow()

    val availablePids = listOf("0C", "0D", "05", "04", "11")

    init {
        loadSession()
        loadReadingsForPid(_selectedPid.value)
    }

    private fun loadSession() {
        viewModelScope.launch {
            _session.value = sessionDao.getById(sessionId)
        }
    }

    fun selectPid(pid: String) {
        _selectedPid.value = pid
        loadReadingsForPid(pid)
    }

    private fun loadReadingsForPid(pid: String) {
        viewModelScope.launch {
            _readings.value = readingDao.getByPid(sessionId, pid)
        }
    }

    fun getStatistics(): PidStatistics {
        val values = readings.value.map { it.value }
        return if (values.isEmpty()) {
            PidStatistics(0f, 0f, 0f, 0)
        } else {
            PidStatistics(
                average = values.average().toFloat(),
                max     = values.maxOrNull() ?: 0f,
                min     = values.minOrNull() ?: 0f,
                count   = values.size
            )
        }
    }

    fun getPidName(pid: String): String = Obd2Decoder.SUPPORTED_PIDS[pid]?.name ?: pid

    // ── Legacy CSV export ───────────────────────────────────────────────
    fun exportToCsv(context: Context) {
        viewModelScope.launch {
            _exportStatus.value = ExportStatus.Loading
            try {
                val readingsList = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    readingDao.getBySession(sessionId)
                }
                if (readingsList.isEmpty()) {
                    _exportStatus.value = ExportStatus.Error("No hay datos para exportar")
                    return@launch
                }
                val sb = StringBuilder()
                sb.append("timestamp,pid,name,value,unit\n")
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                readingsList.forEach { r ->
                    sb.append("${sdf.format(java.util.Date(r.timestamp))},${r.pid},${r.name},${r.value},${r.unit}\n")
                }
                val fileName = "obelus_session_${sessionId}_${System.currentTimeMillis()}.csv"
                val file = java.io.File(context.cacheDir, fileName)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { file.writeText(sb.toString()) }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Exportar Sesión CSV"))
                _exportStatus.value = ExportStatus.Success
            } catch (e: Exception) {
                e.printStackTrace()
                _exportStatus.value = ExportStatus.Error(e.message ?: "Error desconocido al exportar")
            }
        }
    }

    // ── PDF export ──────────────────────────────────────────────────────

    /**
     * Exports the session to PDF at the given SAF Uri.
     * Progress is reported via [exportProgress] StateFlow.
     */
    fun exportToPdf(context: Context, outputUri: Uri) {
        viewModelScope.launch {
            val session  = _session.value ?: return@launch
            val readings = readingDao.getBySession(sessionId)
            val dtcs     = loadDtcs()
            exportManager.exportToPdf(context, session, readings, dtcs, outputUri)
                .collect { progress -> _exportProgress.value = progress }
        }
    }

    // ── Excel export ────────────────────────────────────────────────────

    /**
     * Exports the session to Excel (.xlsx) at the given SAF Uri.
     * Progress is reported via [exportProgress] StateFlow.
     */
    fun exportToExcel(context: Context, outputUri: Uri) {
        viewModelScope.launch {
            val session  = _session.value ?: return@launch
            val readings = readingDao.getBySession(sessionId)
            val dtcs     = loadDtcs()
            exportManager.exportToExcel(context, session, readings, dtcs, outputUri)
                .collect { progress -> _exportProgress.value = progress }
        }
    }

    fun clearExportProgress() {
        _exportProgress.value = null
    }

    private suspend fun loadDtcs(): List<DtcCode> {
        return try {
            dtcCodeDao.getBySession(sessionId.toString())
        } catch (e: Exception) {
            emptyList()
        }
    }
}
