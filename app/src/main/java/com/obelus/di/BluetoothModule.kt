package com.obelus.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.obelus.protocol.BluetoothElmConnection
import com.obelus.protocol.ElmConnection
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothModule {

    @Binds
    @Singleton
    abstract fun bindElmConnection(
        impl: BluetoothElmConnection
    ): ElmConnection

    companion object {
        @Provides
        @Singleton
        fun provideBluetoothAdapter(
            @ApplicationContext context: Context
        ): BluetoothAdapter? {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            return manager.adapter
        }
    }
}
