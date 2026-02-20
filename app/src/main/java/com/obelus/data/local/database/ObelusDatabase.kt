package com.obelus.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.obelus.data.local.dao.*
import com.obelus.data.local.entity.*

// ─────────────────────────────────────────────────────────────────────────────
// Migration v5 → v6
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Adds DBC editor tables and extends can_signals with new v6 columns.
 *
 * Strategy:
 *   1. Create dbc_definitions table
 *   2. Create dbc_signal_overrides table
 *   3. Add nullable columns to can_signals (backward-compatible; old rows get defaults)
 *   4. Add new indices on can_signals for the new FK and isCustom columns
 *
 * Note: fallbackToDestructiveMigration() is kept for development convenience.
 * In production, switch to addMigrations(MIGRATION_5_6) and remove fallback.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // 1. Create dbc_definitions -----------------------------------------
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `dbc_definitions` (
                `id`          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name`        TEXT    NOT NULL,
                `description` TEXT,
                `createdAt`   INTEGER NOT NULL,
                `updatedAt`   INTEGER NOT NULL,
                `isBuiltIn`   INTEGER NOT NULL DEFAULT 0,
                `sourceFile`  TEXT,
                `protocol`    TEXT    NOT NULL DEFAULT 'CAN',
                `signalCount` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dbc_definitions_name`      ON `dbc_definitions` (`name`)")
        db.execSQL("CREATE        INDEX IF NOT EXISTS `index_dbc_definitions_isBuiltIn` ON `dbc_definitions` (`isBuiltIn`)")
        db.execSQL("CREATE        INDEX IF NOT EXISTS `index_dbc_definitions_updatedAt` ON `dbc_definitions` (`updatedAt`)")

        // 2. Create dbc_signal_overrides ------------------------------------
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `dbc_signal_overrides` (
                `id`               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `dbcDefinitionId`  INTEGER NOT NULL,
                `canSignalId`      INTEGER NOT NULL,
                `nameOverride`     TEXT,
                `unitOverride`     TEXT,
                `alertMaxValue`    REAL,
                `alertMinValue`    REAL,
                `notes`            TEXT,
                `showInDashboard`  INTEGER NOT NULL DEFAULT 0,
                `updatedAt`        INTEGER NOT NULL,
                FOREIGN KEY(`dbcDefinitionId`) REFERENCES `dbc_definitions`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`canSignalId`)     REFERENCES `can_signals`(`id`)     ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX  IF NOT EXISTS `index_dbc_signal_overrides_dbcDefinitionId`               ON `dbc_signal_overrides` (`dbcDefinitionId`)")
        db.execSQL("CREATE INDEX  IF NOT EXISTS `index_dbc_signal_overrides_canSignalId`                   ON `dbc_signal_overrides` (`canSignalId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dbc_signal_overrides_dbcDefinitionId_canSignalId` ON `dbc_signal_overrides` (`dbcDefinitionId`, `canSignalId`)")

        // 3. Extend can_signals with v6 columns -----------------------------
        db.execSQL("ALTER TABLE `can_signals` ADD COLUMN `dbcDefinitionId` INTEGER DEFAULT NULL REFERENCES `dbc_definitions`(`id`) ON DELETE SET NULL")
        db.execSQL("ALTER TABLE `can_signals` ADD COLUMN `isCustom`        INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `can_signals` ADD COLUMN `userNotes`       TEXT")

        // 4. Add indices for the new can_signals columns --------------------
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_can_signals_dbcDefinitionId` ON `can_signals` (`dbcDefinitionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_can_signals_isCustom`        ON `can_signals` (`isCustom`)")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Migration v6 → v7: decoded_signals table
// ─────────────────────────────────────────────────────────────────────────────

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `decoded_signals` (
                `id`                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `frameId`           INTEGER NOT NULL,
                `sessionId`         TEXT    NOT NULL,
                `signalId`          INTEGER NOT NULL,
                `signalName`        TEXT    NOT NULL,
                `canId`             TEXT    NOT NULL,
                `timestamp`         INTEGER NOT NULL,
                `rawBits`           INTEGER NOT NULL,
                `calculatedValue`   REAL    NOT NULL,
                `unit`              TEXT,
                `dbcDefinitionId`   INTEGER NOT NULL,
                FOREIGN KEY(`signalId`)        REFERENCES `can_signals`(`id`)      ON DELETE CASCADE,
                FOREIGN KEY(`dbcDefinitionId`) REFERENCES `dbc_definitions`(`id`)  ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_decoded_sessionId`      ON `decoded_signals` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_decoded_signalId`       ON `decoded_signals` (`signalId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_decoded_dbcDefId`       ON `decoded_signals` (`dbcDefinitionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_decoded_session_dbc`    ON `decoded_signals` (`sessionId`, `dbcDefinitionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_decoded_signal_session` ON `decoded_signals` (`signalId`, `sessionId`)")
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
        CanFrameEntity::class,            // v3: Log Viewer
        ManufacturerDtcEntity::class,     // v4: Manufacturer DTC DB
        RaceRecord::class,                // v5: Race Mode
        RaceTelemetryPoint::class,        // v5: Race Telemetry
        DbcDefinition::class,             // v6: DBC Editor
        DbcSignalOverride::class,         // v6: DBC Editor overrides
        DecodedSignal::class              // v7: DBC-Log decoded results
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ObelusDatabase : RoomDatabase() {
    abstract fun canSignalDao(): CanSignalDao
    abstract fun signalReadingDao(): SignalReadingDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun dtcCodeDao(): DtcCodeDao
    abstract fun databaseFileDao(): DatabaseFileDao
    abstract fun canFrameDao(): CanFrameDao                          // v3
    abstract fun manufacturerDtcDao(): ManufacturerDtcDao            // v4
    abstract fun raceRecordDao(): RaceRecordDao                      // v5
    abstract fun raceTelemetryPointDao(): RaceTelemetryPointDao      // v5
    abstract fun dbcDefinitionDao(): DbcDefinitionDao                // v6
    abstract fun decodedSignalDao(): DecodedSignalDao                // v7
}
