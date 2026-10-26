package com.example.zar.di

import com.example.zar.core.domain.repository.AmountPrivacyRepository
import com.example.zar.core.data.local.SharedPreferencesAmountPrivacyRepository
import android.content.Context
import androidx.room.Room
import com.example.zar.features.capture.data.auto.CaptureStagingDatabase
import com.example.zar.features.capture.data.auto.DataStoreAutoCapturePreferences
import com.example.zar.features.capture.data.auto.RoomSmsStagingRepository
import com.example.zar.features.capture.data.auto.StagedSmsDao
import com.example.zar.features.capture.data.auto.WorkManagerAutoCaptureScheduler
import com.example.zar.features.capture.domain.auto.AutoCapturePreferences
import com.example.zar.features.capture.domain.auto.AutoCaptureScheduler
import com.example.zar.features.capture.domain.auto.PauseAutoCaptureOnSignOut
import com.example.zar.features.capture.domain.auto.SmsStagingRepository
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.zar.core.data.local.AndroidConnectivityMonitor
import com.example.zar.core.data.remote.FirebaseUserSessionProvider
import com.example.zar.core.data.remote.firestore.FirestoreDraftRepository
import com.example.zar.core.data.remote.firestore.FirestoreExpectedPaymentRepository
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.ConnectivityMonitor
import com.example.zar.core.domain.repository.DraftRepository
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
import com.example.zar.features.bills.data.repository.DataStoreSuggestionPreferences
import com.example.zar.features.bills.data.repository.FirestoreBillRepository
import com.example.zar.features.bills.data.repository.ResetSuggestionsOnSignOut
import com.example.zar.features.bills.domain.usecase.SuggestionPreferencesRepository
import com.example.zar.features.bills.domain.repository.BillRepository
import com.example.zar.features.crypto.data.repository.FirestoreOpeningCostRepository
import com.example.zar.features.crypto.domain.repository.OpeningCostRepository
import com.example.zar.features.freelance.data.repository.FirestoreProjectRepository
import com.example.zar.features.freelance.domain.repository.ProjectRepository
import com.example.zar.features.allowance.data.repository.FirestoreAllowanceRepository
import com.example.zar.features.export.data.ContentResolverExportFileWriter
import com.example.zar.features.export.domain.repository.ExportFileWriter
import com.example.zar.features.allowance.domain.repository.AllowanceRepository
import com.example.zar.features.plans.data.repository.FirestoreGoalRepository
import com.example.zar.features.plans.domain.repository.GoalRepository
import com.example.zar.features.quicknote.data.remote.HttpQuickNoteDraftTransport
import com.example.zar.features.reminders.data.DataStoreReminderPreferencesRepository
import com.example.zar.features.reminders.data.ResetRemindersOnSignOut
import com.example.zar.features.reminders.data.WorkManagerPaymentReminderScheduler
import com.example.zar.features.reminders.domain.PaymentReminderScheduler
import com.example.zar.features.reminders.domain.ReminderPreferencesRepository
import com.example.zar.features.quicknote.data.remote.QuickNoteDraftTransport
import com.example.zar.features.quicknote.data.repository.CallableQuickNoteDraftRepository
import com.example.zar.features.quicknote.domain.repository.QuickNoteDraftRepository
import com.example.zar.features.rules.data.FirestoreCategoryRuleRepository
import com.example.zar.features.rules.data.InMemoryRuleOfferInbox
import com.example.zar.features.rules.domain.repository.CategoryRuleRepository
import com.example.zar.features.rules.domain.repository.RuleOfferInbox
import com.example.zar.features.settings.data.repository.ReportingCurrencyPreferencesRepository
import com.example.zar.features.settings.data.repository.SharedPreferencesDisplayPreferencesRepository
import com.example.zar.features.transactions.data.category.FirestoreExpenseCategoryRepository
import com.example.zar.features.transactions.domain.category.repository.ExpenseCategoryRepository
import com.example.zar.features.settings.domain.repository.AppPreferencesRepository
import com.example.zar.features.settings.domain.repository.DisplayPreferencesRepository
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
    @IntoSet
    abstract fun bindClearSharedTextOnSignOut(impl: com.example.zar.features.capture.presentation.SharedTextInbox): SignOutHandler

    @Binds
    @Singleton
    abstract fun bindDraftRepository(impl: FirestoreDraftRepository): DraftRepository

    @Binds
    @Singleton
    abstract fun bindExpectedPaymentRepository(impl: FirestoreExpectedPaymentRepository): ExpectedPaymentRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: FirestoreProjectRepository): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindBillRepository(impl: FirestoreBillRepository): BillRepository

    @Binds
    @Singleton
    abstract fun bindOpeningCostRepository(impl: FirestoreOpeningCostRepository): OpeningCostRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: FirestoreAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindGoalRepository(impl: FirestoreGoalRepository): GoalRepository

    @Binds
    @Singleton
    abstract fun bindAllowanceRepository(impl: FirestoreAllowanceRepository): AllowanceRepository

    @Binds
    @Singleton
    abstract fun bindAmountPrivacyRepository(impl: SharedPreferencesAmountPrivacyRepository): AmountPrivacyRepository

    @Binds
    @Singleton
    abstract fun bindExportFileWriter(impl: ContentResolverExportFileWriter): ExportFileWriter

    @Binds
    @Singleton
    abstract fun bindQuickNoteDraftTransport(impl: HttpQuickNoteDraftTransport): QuickNoteDraftTransport

    @Binds
    @Singleton
    abstract fun bindQuickNoteDraftRepository(impl: CallableQuickNoteDraftRepository): QuickNoteDraftRepository

    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(impl: AndroidConnectivityMonitor): ConnectivityMonitor

    @Binds
    @Singleton
    abstract fun bindAppPreferencesRepository(
        impl: ReportingCurrencyPreferencesRepository,
    ): AppPreferencesRepository

    /** Device-local and deliberately not a [SignOutHandler]: appearance survives sign-out and account switching. */
    @Binds
    @Singleton
    abstract fun bindDisplayPreferencesRepository(
        impl: SharedPreferencesDisplayPreferencesRepository,
    ): DisplayPreferencesRepository

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

    @Binds
    @Singleton
    abstract fun bindReminderPreferencesRepository(impl: DataStoreReminderPreferencesRepository): ReminderPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindPaymentReminderScheduler(impl: WorkManagerPaymentReminderScheduler): PaymentReminderScheduler

    @Binds
    @IntoSet
    abstract fun bindResetRemindersOnSignOut(impl: ResetRemindersOnSignOut): SignOutHandler

    @Binds
    @Singleton
    abstract fun bindCategoryRuleRepository(impl: FirestoreCategoryRuleRepository): CategoryRuleRepository

    @Binds
    @Singleton
    abstract fun bindExpenseCategoryRepository(impl: FirestoreExpenseCategoryRepository): ExpenseCategoryRepository

    @Binds
    @Singleton
    abstract fun bindRuleOfferInbox(impl: InMemoryRuleOfferInbox): RuleOfferInbox

    @Binds
    @IntoSet
    abstract fun bindClearRuleOfferOnSignOut(impl: InMemoryRuleOfferInbox): SignOutHandler

    @Binds
    @Singleton
    abstract fun bindSuggestionPreferences(impl: DataStoreSuggestionPreferences): SuggestionPreferencesRepository

    @Binds
    @IntoSet
    abstract fun bindResetSuggestionsOnSignOut(impl: ResetSuggestionsOnSignOut): SignOutHandler

    @Binds
    @Singleton
    abstract fun bindSmsStagingRepository(impl: RoomSmsStagingRepository): SmsStagingRepository

    @Binds
    @Singleton
    abstract fun bindAutoCapturePreferences(impl: DataStoreAutoCapturePreferences): AutoCapturePreferences

    @Binds
    @Singleton
    abstract fun bindAutoCaptureScheduler(impl: WorkManagerAutoCaptureScheduler): AutoCaptureScheduler

    @Binds
    @IntoSet
    abstract fun bindPauseAutoCaptureOnSignOut(impl: PauseAutoCaptureOnSignOut): SignOutHandler

    companion object {
        @Provides
        @Singleton
        fun provideCaptureStagingDatabase(@ApplicationContext context: Context): CaptureStagingDatabase =
            Room.databaseBuilder(context, CaptureStagingDatabase::class.java, CaptureStagingDatabase.NAME).build()

        @Provides
        fun provideStagedSmsDao(database: CaptureStagingDatabase): StagedSmsDao = database.stagedSms()
    }
}
