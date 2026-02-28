package com.obelus.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.obelus.data.local.dao.*
import com.obelus.data.local.entity.*

// ─────────────────────────────────────────────────────────────────────────────
// Migrations History
// ─────────────────────────────────────────────────────────────────────────────

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `dbc_definitions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isBuiltIn` INTEGER NOT NULL DEFAULT 0, `sourceFile` TEXT, `protocol` TEXT NOT NULL DEFAULT 'CAN', `signalCount` INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `dbc_signal_overrides` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dbcDefinitionId` INTEGER NOT NULL, `canSignalId` INTEGER NOT NULL, `nameOverride` TEXT, `unitOverride` TEXT, `alertMaxValue` REAL, `alertMinValue` REAL, `notes` TEXT, `showInDashboard` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`dbcDefinitionId`) REFERENCES `dbc_definitions`(`id`) ON DELETE CASCADE, FOREIGN KEY(`canSignalId`) REFERENCES `can_signals`(`id`) ON DELETE CASCADE)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `decoded_signals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `frameId` INTEGER NOT NULL, `sessionId` TEXT NOT NULL, `signalId` INTEGER NOT NULL, `signalName` TEXT NOT NULL, `canId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `rawBits` INTEGER NOT NULL, `calculatedValue` REAL NOT NULL, `unit` TEXT, `dbcDefinitionId` INTEGER NOT NULL, FOREIGN KEY(`signalId`) REFERENCES `can_signals`(`id`) ON DELETE CASCADE, FOREIGN KEY(`dbcDefinitionId`) REFERENCES `dbc_definitions`(`id`) ON DELETE CASCADE)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `security_access_logs` (
                `id`        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `ecuName`   TEXT    NOT NULL,
                `brand`     TEXT    NOT NULL,
                `algorithm` TEXT    NOT NULL,
                `isSuccess` INTEGER NOT NULL,
                `vin`       TEXT    NOT NULL,
                `details`   TEXT
            )
        """.trimIndent())
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `diagnostic_rules` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `dtcCode` TEXT NOT NULL,
                `requiredConditions` TEXT NOT NULL,
                `weight` REAL NOT NULL,
                `probableCause` TEXT NOT NULL,
                `isRootCandidate` INTEGER NOT NULL,
                `severityLevel` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Database declaration
// ─────────────────────────────────────────────────────────────────────────────

@Database(
    entities = [
        CanSignal::class,
        SignalReading::class,
        ScanSession::class,
        DtcCode::class,
        DatabaseFile::class,
        CanFrameEntity::class,
        ManufacturerDtcEntity::class,
        RaceRecord::class,
        RaceTelemetryPoint::class,
        DbcDefinition::class,
        DbcSignalOverride::class,
        DecodedSignal::class,
        SecurityAccessLog::class,
        DiagnosticRule::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ObelusDatabase : RoomDatabase() {
    abstract fun canSignalDao(): CanSignalDao
    abstract fun signalReadingDao(): SignalReadingDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun dtcCodeDao(): DtcCodeDao
    abstract fun databaseFileDao(): DatabaseFileDao
    abstract fun canFrameDao(): CanFrameDao
    abstract fun manufacturerDtcDao(): ManufacturerDtcDao
    abstract fun raceRecordDao(): RaceRecordDao
    abstract fun raceTelemetryPointDao(): RaceTelemetryPointDao
    abstract fun dbcDefinitionDao(): DbcDefinitionDao
    abstract fun decodedSignalDao(): DecodedSignalDao
    abstract fun securityAccessDao(): SecurityAccessDao
    abstract fun diagnosticRuleDao(): DiagnosticRuleDao
}
