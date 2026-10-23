package com.example.zar.features.home.domain.usecase

import com.example.zar.features.bills.domain.usecase.ObserveNextBillUseCase
import com.example.zar.features.freelance.domain.usecase.ObserveOutstandingUseCase
import com.example.zar.features.home.domain.model.HomeCommitments
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Clients-owe-you totals and the next bill due, recomputed whenever a milestone, receipt,
 * correction, undo or bill payment is saved (including offline). Both are read through the
 * freelance and bills use cases, so Home follows the same rules as their own screens.
 */
class ObserveHomeCommitmentsUseCase @Inject constructor(
    private val observeOutstanding: ObserveOutstandingUseCase,
    private val observeNextBill: ObserveNextBillUseCase,
) {
    operator fun invoke(today: LocalDate): Flow<HomeCommitments> = combine(
        observeOutstanding(today),
        observeNextBill(today),
    ) { outstanding, nextBill -> HomeCommitments(outstanding, nextBill) }
}
