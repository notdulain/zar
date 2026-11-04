package com.example.zar.features.spending.domain.usecase

import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.spending.domain.model.SpendingBreakdownCalculator
import com.example.zar.features.spending.domain.model.SpendingBreakdownReport
import com.example.zar.features.transactions.domain.category.usecase.ObserveExpenseCategoryCatalogUseCase
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Live spending breakdown for a month. Re-emits when a record, correction, undo or custom category changes. */
class ObserveSpendingBreakdownUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val categories: ObserveExpenseCategoryCatalogUseCase,
) {
    operator fun invoke(month: YearMonth): Flow<SpendingBreakdownReport> =
        combine(ledger.observeLedger(), categories()) { entries, catalog ->
            SpendingBreakdownCalculator.calculate(entries, month, catalog)
        }
}
