package com.obelus.di

import com.obelus.data.repository.ObdRepository
import com.obelus.data.repository.ObdRepositoryImpl
import com.obelus.data.protocol.wifi.WifiConnection
import com.obelus.data.protocol.wifi.Elm327WifiConnection
import com.obelus.protocol.usb.UsbElmConnection
import com.obelus.protocol.ElmConnection
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
         @Named("wifi") wifiConnection: WifiConnection,
         usbConnection: UsbElmConnection
     ): ObdRepository = ObdRepositoryImpl(bluetoothConnection, usbConnection, wifiConnection)

    @Provides
    @Singleton
    @Named("wifi")
    fun provideWifiConnection(impl: Elm327WifiConnection): WifiConnection = impl

}
