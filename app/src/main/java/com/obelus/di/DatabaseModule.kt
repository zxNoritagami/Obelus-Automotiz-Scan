package com.obelus.di

import android.content.Context
import androidx.room.Room
import com.obelus.data.local.dao.*
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
        ).fallbackToDestructiveMigration() // Useful for dev
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
}
