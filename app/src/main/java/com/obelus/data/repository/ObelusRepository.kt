package com.obelus.data.repository

import com.obelus.data.local.entity.*
import com.obelus.data.local.model.SignalStats

interface ObelusRepository {
    // Signals
    suspend fun getSignals(): List<CanSignal>
    suspend fun getSignalById(id: Long): CanSignal?
    suspend fun addSignal(signal: CanSignal): Long
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)

    // Readings
    suspend fun saveReading(reading: SignalReading): Long
    suspend fun getReadingsBySession(sessionId: String): List<SignalReading>
    suspend fun getSignalStats(signalId: Long, since: Long): SignalStats

    // Sessions
    suspend fun startSession(session: ScanSession)
    suspend fun endSession(session: ScanSession)
    suspend fun getActiveSession(): ScanSession?
    suspend fun getAllSessions(): List<ScanSession>

    // DTCs
    suspend fun saveDtc(dtc: DtcCode)
    suspend fun getActiveDtcs(): List<DtcCode>
    suspend fun clearDtc(code: String, timestamp: Long)

    // Files
    suspend fun importDatabaseFile(file: DatabaseFile)
    suspend fun getActiveDatabaseFiles(): List<DatabaseFile>
}
