package com.obelus.di

import android.content.Context
import com.obelus.mqtt.ObdMqttClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para la capa MQTT.
 * Provee [ObdMqttClient] como singleton de aplicación.
 */
@Module
@InstallIn(SingletonComponent::class)
object MqttModule {

    @Provides
    @Singleton
    fun provideObdMqttClient(
        @ApplicationContext context: Context
    ): ObdMqttClient = ObdMqttClient(context)
}
