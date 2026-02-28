package com.obelus.data.sniffer

import android.content.Context
import android.util.Log
import com.obelus.data.repository.ObdRepository
import com.obelus.domain.model.CanFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanSnifferEngine @Inject constructor(
    private val obdRepository: ObdRepository
) {
    private val TAG = "CanSnifferEngine"
    
    private val _activeFrames = MutableStateFlow<Map<String, CanFrame>>(emptyMap())
    val activeFrames = _activeFrames.asStateFlow()

    private val _isDiffMode = MutableStateFlow(false)
    val isDiffMode = _isDiffMode.asStateFlow()

    private var baselineFrames: Map<String, CanFrame> = emptyMap()
    private val frameStats = mutableMapOf<String, Pair<Int, Long>>() // ID -> (Count, LastTimestamp)

    private var snifferJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var isFrozen = false

    fun startSniffing() {
        if (snifferJob?.isActive == true) return
        isFrozen = false
        
        snifferJob = scope.launch {
            try {
                obdRepository.sendCommand("AT SP 6")
                obdRepository.sendCommand("AT H1")
                obdRepository.sendCommand("AT CAF 0")
                
                while (isActive) {
                    if (!isFrozen) {
                        val rawLine = obdRepository.sendCommand("AT MA")
                        processRawData(rawLine)
                    }
                    delay(33) 
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sniffer error", e)
            }
        }
    }

    fun stopSniffing() {
        snifferJob?.cancel()
        scope.launch {
            obdRepository.sendCommand("AT")
            obdRepository.sendCommand("AT AR")
        }
    }

    fun toggleFreeze() {
        isFrozen = !isFrozen
    }

    fun captureBaseline() {
        baselineFrames = _activeFrames.value.toMap()
        Log.d(TAG, "Baseline captured: ${baselineFrames.size} IDs")
    }

    fun toggleDiffMode() {
        _isDiffMode.value = !_isDiffMode.value
    }

    private fun processRawData(rawLine: String) {
        if (isFrozen || rawLine.isBlank() || rawLine.contains("SEARCHING")) return

        rawLine.split("\r", "\n").forEach { line ->
            val cleanLine = line.trim()
            if (cleanLine.length >= 3) {
                parseAndEmitFrame(cleanLine)
            }
        }
    }

    private fun parseAndEmitFrame(line: String) {
        try {
            val parts = line.split(" ")
            if (parts.size < 2) return

            val id = parts[0]
            val data = parts.drop(1).mapNotNull { 
                try { it.toInt(16).toByte() } catch (e: Exception) { null } 
            }.toByteArray()
            
            if (data.isEmpty()) return

            val now = System.currentTimeMillis()
            
            // 1. Frequency Analysis
            val stats = frameStats[id] ?: Pair(0, 0L)
            val newCount = stats.first + 1
            val freq = if (stats.second > 0) {
                1000f / (now - stats.second)
            } else 0f
            frameStats[id] = Pair(newCount, now)

            val currentMap = _activeFrames.value.toMutableMap()
            val previousFrame = currentMap[id]
            
            // 2. Heuristic Decoding
            val tag = runHeuristics(id, data, previousFrame?.data)

            var mask = 0
            if (previousFrame != null) {
                for (i in 0 until minOf(data.size, previousFrame.data.size)) {
                    if (data[i] != previousFrame.data[i]) {
                        mask = mask or (1 shl i)
                    }
                }
            } else {
                mask = 0xFF
            }

            val newFrame = CanFrame(
                id = id,
                data = data,
                timestamp = now,
                lastChanged = if (mask != 0) now else (previousFrame?.lastChanged ?: now),
                changedBytesMask = mask,
                frequencyHz = freq,
                frameCount = newCount,
                heuristicTag = tag
            )

            // 4. Differential Mode Filtering
            if (_isDiffMode.value) {
                val baseline = baselineFrames[id]
                if (baseline != null && baseline.data.contentEquals(data)) {
                    // Same as baseline, ignore or mark as hidden in UI
                    return
                }
            }

            currentMap[id] = newFrame
            _activeFrames.value = currentMap
        } catch (e: Exception) { }
    }

    private fun runHeuristics(id: String, data: ByteArray, prevData: ByteArray?): String? {
        if (prevData == null) return null
        
        // Example: Throttle 0 -> 255
        for (i in data.indices) {
            val current = data[i].toInt() and 0xFF
            val prev = if (i < prevData.size) prevData[i].toInt() and 0xFF else 0
            if (prev == 0 && current > 200) return "EVENT: TRIGGER?"
            if (Math.abs(current - prev) > 10 && id.startsWith("7")) return "UDS RESPONSE?"
        }
        
        if (data.size == 4) return "COUNTER/TS?"
        
        return null
    }

    fun exportToAsc(context: Context): File? {
        try {
            val sdf = SimpleDateFormat("EEE MMM dd hh:mm:ss.SSS a yyyy", Locale.US)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(context.getExternalFilesDir(null), "obelus_capture_$timestamp.asc")
            
            val sb = StringBuilder()
            sb.append("date ${sdf.format(Date())}\n")
            sb.append("base hex  timestamps absolute\n")
            sb.append("internal events logged\n")
            sb.append("// Version 1.0.0\n")
            
            _activeFrames.value.values.forEach { frame ->
                val time = (frame.timestamp / 1000.0)
                val hexData = frame.data.joinToString(" ") { String.format("%02X", it) }
                sb.append(String.format(Locale.US, "%10.6f 1  %3s             Rx   d %d %s\n", 
                    time, frame.id, frame.data.size, hexData))
            }
            
            file.writeText(sb.toString())
            return file
        } catch (e: Exception) {
            return null
        }
    }
}
