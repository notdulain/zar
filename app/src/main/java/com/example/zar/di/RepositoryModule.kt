package com.example.zar.di

import com.example.zar.core.data.local.AndroidConnectivityMonitor
import com.example.zar.core.data.remote.FirebaseUserSessionProvider
import com.example.zar.core.data.remote.firestore.FirestoreExpectedPaymentRepository
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.ConnectivityMonitor
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.accounts.data.repository.FirestoreAccountRepository
import com.example.zar.features.auth.data.repository.FirebaseAuthRepository
import com.example.zar.features.auth.domain.repository.AuthRepository
import com.example.zar.features.auth.domain.repository.SignOutHandler
import com.example.zar.features.catchup.data.DataStoreCatchUpPreferencesRepository
import com.example.zar.features.catchup.data.ResetCatchUpOnSignOut
import com.example.zar.features.catchup.data.WorkManagerReminderScheduler
import com.example.zar.features.catchup.domain.repository.CatchUpPreferencesRepository
import com.example.zar.features.catchup.domain.repository.ReminderScheduler
import com.example.zar.features.freelance.data.repository.FirestoreProjectRepository
import com.example.zar.features.freelance.domain.repository.ProjectRepository
import com.example.zar.features.plans.data.repository.FirestoreGoalRepository
import com.example.zar.features.plans.domain.repository.GoalRepository
import com.example.zar.features.quicknote.data.remote.FirebaseQuickNoteDraftTransport
import com.example.zar.features.quicknote.data.remote.QuickNoteDraftTransport
import com.example.zar.features.quicknote.data.repository.CallableQuickNoteDraftRepository
import com.example.zar.features.quicknote.domain.repository.QuickNoteDraftRepository
import com.example.zar.features.settings.data.repository.InMemoryAppPreferencesRepository
import com.example.zar.features.settings.domain.repository.AppPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
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
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    /** Features add their own handlers with `@Binds @IntoSet`; the set may be empty. */
    @Multibinds
    abstract fun signOutHandlers(): Set<SignOutHandler>

    @Binds
    @Singleton
    abstract fun bindLedgerRepository(impl: FirestoreLedgerRepository): LedgerRepository

    @Binds
    @Singleton
    abstract fun bindExpectedPaymentRepository(impl: FirestoreExpectedPaymentRepository): ExpectedPaymentRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: FirestoreProjectRepository): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: FirestoreAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindGoalRepository(impl: FirestoreGoalRepository): GoalRepository

    @Binds
    @Singleton
    abstract fun bindQuickNoteDraftTransport(impl: FirebaseQuickNoteDraftTransport): QuickNoteDraftTransport

    @Binds
    @Singleton
    abstract fun bindQuickNoteDraftRepository(impl: CallableQuickNoteDraftRepository): QuickNoteDraftRepository

    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(impl: AndroidConnectivityMonitor): ConnectivityMonitor

    @Binds
    @Singleton
    abstract fun bindAppPreferencesRepository(
        impl: InMemoryAppPreferencesRepository,
    ): AppPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindCatchUpPreferencesRepository(
        impl: DataStoreCatchUpPreferencesRepository,
    ): CatchUpPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    @IntoSet
    abstract fun bindResetCatchUpOnSignOut(impl: ResetCatchUpOnSignOut): SignOutHandler
}
