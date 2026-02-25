package com.obelus.di

import com.obelus.data.repository.ObdRepository
import com.obelus.data.repository.ObdRepositoryImpl
import com.obelus.protocol.ElmConnection
import com.obelus.protocol.usb.UsbElmConnection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ObdModule {

    @Provides
    @Singleton
    fun provideObdRepository(
        @Named("bluetooth") bluetoothConnection: ElmConnection,
        usbConnection: UsbElmConnection
    ): ObdRepository = ObdRepositoryImpl(bluetoothConnection, usbConnection)
}
