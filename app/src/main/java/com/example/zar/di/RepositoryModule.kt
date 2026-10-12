package com.example.zar.di

import com.example.zar.core.data.remote.FirebaseUserSessionProvider
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.settings.data.repository.InMemoryAppPreferencesRepository
import com.example.zar.features.settings.domain.repository.AppPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Binds each domain repository interface to its data-layer implementation.
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserSessionProvider(impl: FirebaseUserSessionProvider): UserSessionProvider

    @Binds
    @Singleton
    abstract fun bindLedgerRepository(impl: FirestoreLedgerRepository): LedgerRepository

    @Binds
    @Singleton
    abstract fun bindAppPreferencesRepository(
        impl: InMemoryAppPreferencesRepository,
    ): AppPreferencesRepository
}
