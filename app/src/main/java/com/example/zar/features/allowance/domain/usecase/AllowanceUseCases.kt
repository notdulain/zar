package com.example.zar.features.allowance.domain.usecase

import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.resultOf
import com.example.zar.features.allowance.domain.AllowanceCalculator
import com.example.zar.features.allowance.domain.model.AllowanceOverview
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.repository.AllowanceRepository
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import java.time.LocalDate
import java.time.Instant
import java.time.Duration
import com.example.zar.core.domain.calculation.ZarTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The allowance setting and this week's status. It recomputes whenever a record, correction,
 * undo, refund or bill payment changes, including offline.
 */
class ObserveAllowanceUseCase internal constructor(
    private val allowance: AllowanceRepository,
    private val ledger: LedgerRepository,
    private val expected: ExpectedPaymentRepository,
    private val dates: Flow<LocalDate>,
) {
    @Inject constructor(allowance: AllowanceRepository, ledger: LedgerRepository, expected: ExpectedPaymentRepository) :
        this(allowance, ledger, expected, allowanceDates())

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<AllowanceOverview> = dates.flatMapLatest { invoke(it) }

    operator fun invoke(today: LocalDate): Flow<AllowanceOverview> = combine(
        allowance.observeSettings(),
        ledger.observeLedger(),
        expected.observeExpectedPayments(),
        expected.observeMatches(),
        allowance.observeSyncState(),
    ) { settings, entries, payments, matches, sync ->
        AllowanceOverview(settings, AllowanceCalculator.status(settings, entries, payments, matches, today), sync)
    }
}

/** Saves the allowance setting. Turning it off keeps the amount and categories for next time. */
class SaveAllowanceSettingsUseCase @Inject constructor(
    private val allowance: AllowanceRepository,
) {
    suspend operator fun invoke(settings: AllowanceSettings): Result<Unit> = resultOf {
        // A filter may name a built-in or one of the user's own categories; anything else is a mistake.
        val unknown = settings.categoryIds.filterNot { ExpenseCategory.fromId(it) != null || CustomCategoryIds.isCustom(it) }
        if (unknown.isNotEmpty()) throw LedgerError.InvalidRecord("Unknown category")
        allowance.saveSettings(settings)
    }
}

/** Recheck on resume after a suspended delay and at Colombo midnight, without requiring a ledger write. */
internal fun allowanceDates(now: () -> Instant = Instant::now): Flow<LocalDate> = flow {
    while (true) {
        val instant = now()
        val date = instant.atZone(ZarTime.ZONE).toLocalDate()
        emit(date)
        val midnight = date.plusDays(1).atStartOfDay(ZarTime.ZONE).toInstant()
        delay(minOf(60_000L, Duration.between(instant, midnight).toMillis()).coerceAtLeast(1L))
    }
}.distinctUntilChanged()
