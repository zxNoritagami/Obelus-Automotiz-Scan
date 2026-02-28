package com.obelus.di

import com.obelus.data.repository.*
import com.obelus.domain.repository.*
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

    @Binds
    @Singleton
    abstract fun bindDiagnosticRuleRepository(
        impl: DiagnosticRuleRepositoryImpl
    ): DiagnosticRuleRepository
}
