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
        DatabaseFile::class,
        CanFrameEntity::class,        // v3: Log Viewer
        ManufacturerDtcEntity::class, // v4: Manufacturer DTC DB
        RaceRecord::class,            // v5: Race Mode
        RaceTelemetryPoint::class     // v5: Race Telemetry
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ObelusDatabase : RoomDatabase() {
    abstract fun canSignalDao(): CanSignalDao
    abstract fun signalReadingDao(): SignalReadingDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun dtcCodeDao(): DtcCodeDao
    abstract fun databaseFileDao(): DatabaseFileDao
    abstract fun canFrameDao(): CanFrameDao          // v3
    abstract fun manufacturerDtcDao(): ManufacturerDtcDao  // v4
    abstract fun raceRecordDao(): RaceRecordDao            // v5
    abstract fun raceTelemetryPointDao(): RaceTelemetryPointDao // v5
}
