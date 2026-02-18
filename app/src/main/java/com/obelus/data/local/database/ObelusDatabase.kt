package com.obelus.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.obelus.data.local.dao.*
import com.obelus.data.local.entity.*

@Database(
    entities = [
        CanSignal::class,
        SignalReading::class,
        ScanSession::class,
        DtcCode::class,
        DatabaseFile::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ObelusDatabase : RoomDatabase() {
    abstract fun canSignalDao(): CanSignalDao
    abstract fun signalReadingDao(): SignalReadingDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun dtcCodeDao(): DtcCodeDao
    abstract fun databaseFileDao(): DatabaseFileDao
}
