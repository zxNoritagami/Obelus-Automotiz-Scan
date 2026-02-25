package com.obelus.di

import com.obelus.data.protocol.actuator.ActuatorTestRepository
import com.obelus.data.protocol.actuator.Elm327ActuatorRepository
import com.obelus.protocol.ElmConnection
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ActuatorModule {

    @Binds
    @Singleton
    abstract fun bindActuatorTestRepository(
        impl: Elm327ActuatorRepository
    ): ActuatorTestRepository

    companion object {
        /**
         * Provide [ElmConnection] without a qualifier for [ActuatorTestViewModel].
         * This forwards to the @Named("bluetooth") binding so the ViewModel gets the
         * standard BT connection (USB support can be added later via the ConnectionType mechanism).
         */
        @Provides
        @Singleton
        fun provideDefaultElmConnection(
            @Named("bluetooth") bt: ElmConnection
        ): ElmConnection = bt
    }
}
