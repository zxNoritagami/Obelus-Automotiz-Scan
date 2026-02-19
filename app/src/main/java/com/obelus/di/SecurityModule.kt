package com.obelus.di

import com.obelus.data.protocol.uds.SecurityAccessManager
import com.obelus.data.protocol.uds.UdsSecurityRepository
import com.obelus.obelusscan.data.protocol.UdsProtocol
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecurityAccessManager(
        udsProtocol: UdsProtocol
    ): SecurityAccessManager = SecurityAccessManager(udsProtocol)

    @Provides
    @Singleton
    fun provideUdsSecurityRepository(
        manager: SecurityAccessManager
    ): UdsSecurityRepository = UdsSecurityRepository(manager)
}
