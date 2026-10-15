package com.example.zar.features.home.domain.usecase

import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.home.domain.model.HomeOverview
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Live Home overview for a month. It re-emits whenever a record, correction or undo is saved,
 * including offline, because the ledger is read from Firestore's local cache.
 */
class ObserveHomeOverviewUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    operator fun invoke(month: YearMonth): Flow<HomeOverview> = ledger.observeLedger().map { entries ->
        val period = month.toDateRange()
        val active = entries.active()
        HomeOverview(
            month = month,
            summary = LedgerSummaryCalculator.summarize(entries, period),
            recent = active.take(RECENT_COUNT),
            hasRecordsInMonth = active.any { it.current.date in period },
        )
    }

    companion object {
        const val RECENT_COUNT = 3
    }
}
