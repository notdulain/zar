package com.example.zar.features.allowance.domain.usecase

import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.resultOf
import com.example.zar.features.allowance.domain.AllowanceCalculator
import com.example.zar.features.allowance.domain.model.AllowanceOverview
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.repository.AllowanceRepository
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The allowance setting and this week's status. It recomputes whenever a record, correction,
 * undo, refund or bill payment changes, including offline.
 */
class ObserveAllowanceUseCase @Inject constructor(
    private val allowance: AllowanceRepository,
    private val ledger: LedgerRepository,
    private val expected: ExpectedPaymentRepository,
) {
    operator fun invoke(today: LocalDate): Flow<AllowanceOverview> = combine(
        allowance.observeSettings(),
        ledger.observeLedger(),
        expected.observeExpectedPayments(),
        expected.observeMatches(),
    ) { settings, entries, payments, matches ->
        AllowanceOverview(settings, AllowanceCalculator.status(settings, entries, payments, matches, today))
    }
}

/** Saves the allowance setting. Turning it off keeps the amount and categories for next time. */
class SaveAllowanceSettingsUseCase @Inject constructor(
    private val allowance: AllowanceRepository,
) {
    suspend operator fun invoke(settings: AllowanceSettings): Result<Unit> = resultOf {
        val known = ExpenseCategory.entries.map { it.id }.toSet()
        if (!known.containsAll(settings.categoryIds)) throw LedgerError.InvalidRecord("Unknown category")
        allowance.saveSettings(settings)
    }
}
