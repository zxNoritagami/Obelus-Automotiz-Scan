package com.obelus.di

import android.content.Context
import androidx.room.Room
import com.obelus.data.local.dao.*
import com.obelus.data.local.database.MIGRATION_5_6
import com.obelus.data.local.database.MIGRATION_6_7
import com.obelus.data.local.database.MIGRATION_7_8
import com.obelus.data.local.database.MIGRATION_8_9
import com.obelus.data.local.database.ObelusDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ObelusDatabase {
        return Room.databaseBuilder(
            context,
            ObelusDatabase::class.java,
            "obelus_database"
        )
        .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
        .fallbackToDestructiveMigration()   // keep for dev; remove in production
        .build()
    }

    @Provides
    fun provideCanSignalDao(db: ObelusDatabase): CanSignalDao = db.canSignalDao()

    @Provides
    fun provideSignalReadingDao(db: ObelusDatabase): SignalReadingDao = db.signalReadingDao()

    @Provides
    fun provideScanSessionDao(db: ObelusDatabase): ScanSessionDao = db.scanSessionDao()

    @Provides
    fun provideDtcCodeDao(db: ObelusDatabase): DtcCodeDao = db.dtcCodeDao()

    @Provides
    fun provideDatabaseFileDao(db: ObelusDatabase): DatabaseFileDao = db.databaseFileDao()

    @Provides
    fun provideCanFrameDao(db: ObelusDatabase): CanFrameDao = db.canFrameDao()

    @Provides
    fun provideManufacturerDtcDao(db: ObelusDatabase): ManufacturerDtcDao = db.manufacturerDtcDao()

    @Provides
    fun provideRaceRecordDao(db: ObelusDatabase): RaceRecordDao = db.raceRecordDao()

    @Provides
    fun provideRaceTelemetryPointDao(db: ObelusDatabase): RaceTelemetryPointDao = db.raceTelemetryPointDao()

    @Provides
    fun provideDbcDefinitionDao(db: ObelusDatabase): DbcDefinitionDao = db.dbcDefinitionDao()  // v6

    @Provides
    fun provideDecodedSignalDao(db: ObelusDatabase): DecodedSignalDao = db.decodedSignalDao()  // v7

    @Provides
    fun provideSecurityAccessDao(db: ObelusDatabase): SecurityAccessDao = db.securityAccessDao() // v8
    
    @Provides
    fun provideDiagnosticRuleDao(db: ObelusDatabase): DiagnosticRuleDao = db.diagnosticRuleDao() // v9
}
