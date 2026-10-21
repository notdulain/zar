package com.example.zar.features.catchup.domain.usecase

import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.accounts.domain.reconcile.BalanceCheck
import com.example.zar.features.accounts.domain.usecase.RecordBalanceCheckUseCase
import com.example.zar.features.catchup.domain.ReminderSchedule
import com.example.zar.features.catchup.domain.UnresolvedRules
import com.example.zar.features.catchup.domain.model.CatchUpOverview
import com.example.zar.features.catchup.domain.model.CatchUpPreferences
import com.example.zar.features.catchup.domain.model.ReminderSettings
import com.example.zar.features.catchup.domain.repository.CatchUpPreferencesRepository
import com.example.zar.features.catchup.domain.repository.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Everything the Needs review list and the catch-up flow need: open items, the cash accounts to count and the
 * last catch-up date.
 */
class ObserveCatchUpUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val accounts: AccountRepository,
    private val preferences: CatchUpPreferencesRepository,
) {
    operator fun invoke(): Flow<CatchUpOverview> =
        combine(ledger.observeLedger(), accounts.observeAccounts(), preferences.observe()) { entries, list, prefs ->
            CatchUpOverview(
                items = UnresolvedRules.find(entries, list),
                cashAccounts = list
                    .filter { it.type == AccountType.CASH && !it.isArchived }
                    .sortedBy { it.name.lowercase() }
                    .map { AccountBalanceCalculator.calculate(it, entries) },
                lastCatchUp = prefs.lastCatchUp,
                recordedThisWeek = UnresolvedRules.recordedInLastWeek(entries, ZarTime.today()),
            )
        }
}

/**
 * Finishes a catch-up: compares the cash that was counted with the recorded balance and saves at most one
 * adjustment, marked as a cash catch-up. A match saves nothing. Reuse the same [id] when retrying, so a
 * repeated confirm can't adjust the balance twice. The catch-up date is remembered only once the check worked.
 */
class RecordCatchUpUseCase @Inject constructor(
    private val recordCheck: RecordBalanceCheckUseCase,
    private val preferences: CatchUpPreferencesRepository,
) {
    suspend operator fun invoke(
        id: TransactionId,
        account: Account,
        counted: Money,
        date: LocalDate,
        createdAt: Instant,
    ): Result<BalanceCheck> =
        recordCheck(id, account, counted, date, createdAt, RecordSource.CASH_CATCH_UP).onSuccess {
            // A failure to remember the date must not undo or hide a check that was already saved.
            runCatching { preferences.markCaughtUp(date) }
        }
}

class ObserveCatchUpPreferencesUseCase @Inject constructor(
    private val preferences: CatchUpPreferencesRepository,
) {
    operator fun invoke(): Flow<CatchUpPreferences> = preferences.observe()
}

/** Saves the reminder settings and arranges the next reminder to match them. */
class UpdateReminderSettingsUseCase @Inject constructor(
    private val preferences: CatchUpPreferencesRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(settings: ReminderSettings, now: Instant = Instant.now()) {
        preferences.saveReminder(settings)
        scheduler.schedule(ReminderSchedule.next(settings, now))
    }
}

/** Arranges the next reminder from the saved settings. Run at app start and after each reminder fires. */
class ScheduleReminderUseCase @Inject constructor(
    private val preferences: CatchUpPreferencesRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(now: Instant = Instant.now()) {
        val settings = preferences.observe().first().reminder
        scheduler.schedule(ReminderSchedule.next(settings, now))
    }
}
