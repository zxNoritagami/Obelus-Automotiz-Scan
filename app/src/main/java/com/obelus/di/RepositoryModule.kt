package com.obelus.di

import com.obelus.data.repository.ObelusRepository
import com.obelus.data.repository.ObelusRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindObelusRepository(
        impl: ObelusRepositoryImpl
    ): ObelusRepository
}
