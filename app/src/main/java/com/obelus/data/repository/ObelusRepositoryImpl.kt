package com.obelus.data.repository

import android.util.Log
import com.obelus.data.local.dao.*
import com.obelus.data.local.entity.*
import com.obelus.data.local.model.SignalStats
import javax.inject.Inject

class ObelusRepositoryImpl @Inject constructor(
    private val canSignalDao: CanSignalDao,
    private val signalReadingDao: SignalReadingDao,
    private val scanSessionDao: ScanSessionDao,
    private val dtcCodeDao: DtcCodeDao,
    private val databaseFileDao: DatabaseFileDao
) : ObelusRepository {

    private val TAG = "ObelusRepository"

    // --- Signals ---

    override suspend fun getSignals(): List<CanSignal> {
        return try {
            canSignalDao.getAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching signals", e)
            emptyList()
        }
    }

    override suspend fun getSignalById(id: Long): CanSignal? {
        return try {
            canSignalDao.getById(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching signal by id: $id", e)
            null
        }
    }

    override suspend fun addSignal(signal: CanSignal): Long {
        return try {
            canSignalDao.insert(signal)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding signal", e)
            -1L
        }
    }

    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        try {
            canSignalDao.setFavorite(id, isFavorite)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling favorite for signal: $id", e)
        }
    }

    // --- Readings ---

    override suspend fun saveReading(reading: SignalReading): Long {
        return try {
            signalReadingDao.insert(reading)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving reading", e)
            -1L
        }
    }

    override suspend fun getReadingsBySession(sessionId: String): List<SignalReading> {
        return try {
            signalReadingDao.getBySession(sessionId.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching readings for session: $sessionId", e)
            emptyList()
        }
    }

    override suspend fun getSignalStats(signalId: Long, since: Long): SignalStats {
        return try {
            // Updated to match Dao (pid is String, using signalId as String for now)
            signalReadingDao.getStats(signalId.toString(), since)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching stats for signal: $signalId", e)
            SignalStats(0f, 0f, 0f) // Return default on error
        }
    }

    // --- Sessions ---

    override suspend fun startSession(session: ScanSession) {
        try {
            scanSessionDao.insert(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting session", e)
        }
    }

    override suspend fun endSession(session: ScanSession) {
        try {
            scanSessionDao.update(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error ending session", e)
        }
    }

    override suspend fun getActiveSession(): ScanSession? {
        return try {
            scanSessionDao.getActive()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active session", e)
            null
        }
    }

    override suspend fun getAllSessions(): List<ScanSession> {
        return try {
            scanSessionDao.getAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all sessions", e)
            emptyList()
        }
    }

    // --- DTCs ---

    override suspend fun saveDtc(dtc: DtcCode) {
        try {
            dtcCodeDao.insert(dtc)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving DTC", e)
        }
    }

    override suspend fun getActiveDtcs(): List<DtcCode> {
        return try {
            dtcCodeDao.getAllActive()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active DTCs", e)
            emptyList()
        }
    }

    override suspend fun clearDtc(code: String, timestamp: Long) {
        try {
            dtcCodeDao.clearDtc(code, timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing DTC: $code", e)
        }
    }

    // --- Files ---

    override suspend fun importDatabaseFile(file: DatabaseFile) {
        try {
            databaseFileDao.insert(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing database file", e)
        }
    }

    override suspend fun getActiveDatabaseFiles(): List<DatabaseFile> {
        return try {
            databaseFileDao.getActive()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active database files", e)
            emptyList()
        }
    }
}
