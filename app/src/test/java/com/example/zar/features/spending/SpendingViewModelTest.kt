package com.example.zar.features.spending

import androidx.lifecycle.SavedStateHandle
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.home.StaticLedgerRepository
import com.example.zar.features.spending.domain.usecase.ObserveSpendingBreakdownUseCase
import com.example.zar.features.spending.presentation.viewmodel.SpendingContent
import com.example.zar.features.spending.presentation.viewmodel.SpendingEntriesContent
import com.example.zar.features.spending.presentation.viewmodel.SpendingEntriesViewModel
import com.example.zar.features.spending.presentation.viewmodel.SpendingViewModel
import com.example.zar.features.transactions.domain.activity.usecase.ObserveActivityUseCase
import com.example.zar.core.domain.TestLedger
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpendingViewModelTest {
    private val ledger = StaticLedgerRepository()
    private val categories = com.example.zar.features.transactions.domain.category.usecase.ObserveExpenseCategoryCatalogUseCase(
        com.example.zar.features.transactions.domain.category.FakeExpenseCategoryRepository(),
    )
    private val thisMonth = YearMonth.from(ZarTime.today())

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun lunch(id: String, major: Long, date: java.time.LocalDate) =
        tx(id, TransactionType.EXPENSE, lkr(major), date = date, spendingClass = SpendingClass.DISCRETIONARY).copy(categoryId = "groceries")

    @Test
    fun opensOnTheRequestedMonthAndStepsBackButNeverIntoTheFuture() = runTest {
        ledger.entries.value = LedgerProjector.project(listOf(lunch("a", 500, thisMonth.minusMonths(1).atDay(5))), emptyList(), emptyList())
        val vm = SpendingViewModel(SavedStateHandle(mapOf("month" to thisMonth.minusMonths(1).toString())), ObserveSpendingBreakdownUseCase(ledger, categories))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val first = vm.uiState.value
        assertEquals(thisMonth.minusMonths(1), first.month)
        assertTrue(first.canGoForward)
        assertEquals(lkr(500), ((first.content as SpendingContent.Ready).report.total.amount))

        vm.showNextMonth()
        assertEquals(thisMonth, vm.uiState.value.month)
        assertFalse(vm.uiState.value.canGoForward)
        vm.showNextMonth()
        assertEquals(thisMonth, vm.uiState.value.month)
        vm.showPreviousMonth()
        assertEquals(thisMonth.minusMonths(1), vm.uiState.value.month)
    }

    @Test
    fun aMonthBeyondTodayOrGarbageFallsBackToTheCurrentMonth() = runTest {
        for (arg in listOf(thisMonth.plusMonths(3).toString(), "nonsense")) {
            val vm = SpendingViewModel(SavedStateHandle(mapOf("month" to arg)), ObserveSpendingBreakdownUseCase(ledger, categories))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            assertEquals(thisMonth, vm.uiState.value.month)
        }
    }

    @Test
    fun aFailedLedgerReadShowsAnErrorAndRetryRecovers() = runTest {
        ledger.source = kotlinx.coroutines.flow.flow { throw java.io.IOException("down") }
        val vm = SpendingViewModel(SavedStateHandle(mapOf("month" to thisMonth.toString())), ObserveSpendingBreakdownUseCase(ledger, categories))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(SpendingContent.Error, vm.uiState.value.content)

        ledger.source = null
        vm.retry()
        assertTrue(vm.uiState.value.content is SpendingContent.Ready)
    }

    @Test
    fun entriesForARowAreTheMonthClassAndCategoryOnly() = runTest {
        val accounts = com.example.zar.core.domain.FakeAccountRepository(TestLedger.bank, TestLedger.cash)
        ledger.entries.value = LedgerProjector.project(
            listOf(
                lunch("in", 500, thisMonth.atDay(1)),
                lunch("other-month", 700, thisMonth.minusMonths(1).atDay(1)),
                tx("rent", TransactionType.EXPENSE, lkr(900), date = thisMonth.atDay(1), spendingClass = SpendingClass.COMMITTED).copy(categoryId = "rent"),
            ),
            emptyList(), emptyList(),
        )
        val vm = SpendingEntriesViewModel(
            SavedStateHandle(mapOf("month" to thisMonth.toString(), "spendingClass" to "DISCRETIONARY", "categoryKey" to "groceries")),
            ObserveActivityUseCase(ledger, accounts, categories),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val ready = vm.uiState.value.content as SpendingEntriesContent.Ready
        assertEquals(listOf("in"), ready.list.rows.map { it.entry.id.value })
        assertEquals(SpendingClass.DISCRETIONARY, vm.uiState.value.spendingClass)
    }
}
