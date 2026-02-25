package com.obelus.di

import com.obelus.data.protocol.wifi.Elm327WifiConnection
import com.obelus.data.protocol.wifi.WifiConnection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WifiModule {

    @Binds
    @Singleton
    abstract fun bindWifiConnection(
        elm327WifiConnection: Elm327WifiConnection
    ): WifiConnection
}
