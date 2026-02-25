package com.obelus.di

import android.content.Context
import android.hardware.usb.UsbManager
import com.obelus.protocol.usb.UsbElmConnection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UsbModule {

    @Provides
    @Singleton
    fun provideUsbManager(
        @ApplicationContext context: Context
    ): UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    @Provides
    @Singleton
    fun provideUsbElmConnection(
        @ApplicationContext context: Context,
        usbManager: UsbManager
    ): UsbElmConnection = UsbElmConnection(context, usbManager)
}
