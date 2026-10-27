package com.example.zar.features.transactions.presentation.income.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.transactions.domain.income.model.IncomeCategory
import com.example.zar.features.transactions.domain.income.usecase.ObserveIncomeAccountsUseCase
import com.example.zar.features.transactions.domain.income.usecase.ObserveIncomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class IncomeRow(
    val id: String,
    val receivedFrom: String?,
    val category: IncomeCategory?,
    val accountName: String?,
    val date: LocalDate,
    val amount: Money,
    /** Saved without an LKR rate, so it is left out of the Received total. */
    val missingRate: Boolean,
    val isEdited: Boolean,
)

data class IncomeMonth(val month: YearMonth, val rows: List<IncomeRow>)

data class IncomeListUiState(
    val isLoading: Boolean = true,
    val receivedThisMonth: ReportingTotal? = null,
    val months: List<IncomeMonth> = emptyList(),
    val loadFailed: Boolean = false,
)

@HiltViewModel
class IncomeListViewModel @Inject constructor(
    observeIncome: ObserveIncomeUseCase,
    observeAccounts: ObserveIncomeAccountsUseCase,
) : ViewModel() {

    /** Bumped by [retry]; each value starts a fresh observation of the ledger after a load failure. */
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<IncomeListUiState> = attempt.flatMapLatest { observe(observeIncome, observeAccounts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomeListUiState())

    /** Re-subscribes after "Could not load your income"; shows the loader again until the ledger answers. */
    fun retry() {
        attempt.update { it + 1 }
    }

    private fun observe(observeIncome: ObserveIncomeUseCase, observeAccounts: ObserveIncomeAccountsUseCase) = combine(
        observeIncome(YearMonth.now(ZarTime.ZONE)),
        observeAccounts(),
    ) { overview, accounts ->
        val accountNames = accounts.associate { it.id to it.name }
        val rows = overview.entries.map { entry ->
            val tx = entry.current
            IncomeRow(
                id = entry.id.value,
                receivedFrom = tx.merchant,
                category = IncomeCategory.fromId(tx.categoryId),
                accountName = accountNames[tx.accountId],
                date = tx.date,
                amount = tx.amount,
                missingRate = tx.reportingConversion is ReportingConversion.Missing,
                isEdited = entry.isCorrected,
            )
        }
        IncomeListUiState(
            isLoading = false,
            receivedThisMonth = overview.receivedThisMonth,
            months = rows.groupBy { YearMonth.from(it.date) }.map { (month, list) -> IncomeMonth(month, list) },
        )
    }
        .onStart { emit(IncomeListUiState()) }
        .catch { emit(IncomeListUiState(isLoading = false, loadFailed = true)) }
}
