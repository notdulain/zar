package com.example.zar.features.transactions.presentation.income

import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.FakeAccountRepository
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.transactions.domain.income.usecase.ObserveIncomeAccountsUseCase
import com.example.zar.features.transactions.domain.income.usecase.ObserveIncomeUseCase
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeListViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

/** L10: the Income list across currencies, when empty, and when loading fails then recovers. */
@OptIn(ExperimentalCoroutinesApi::class)
class IncomeListViewModelTest {

    /** The shared fake ledger, failing to load until [broken] is cleared. */
    private class Ledger(val inner: FakeLedgerRepository = FakeLedgerRepository()) : LedgerRepository by inner {
        var broken = false
        override fun observeLedger(): Flow<List<LedgerEntry>> =
            if (broken) flow { throw IOException("Permission denied") } else inner.observeLedger()
    }

    private val ledger = Ledger()
    private val today = ZarTime.today()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = IncomeListViewModel(ObserveIncomeUseCase(ledger), ObserveIncomeAccountsUseCase(FakeAccountRepository(bank, payoneer)))

    @Test
    fun emptyMonthShowsNothingReceived() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.months.isEmpty())
        assertEquals(lkr(0), vm.uiState.value.receivedThisMonth?.amount)
        vm.viewModelScope.cancel()
    }

    @Test
    fun foreignIncomeCountsWithItsRateAndIsFlaggedWithoutOne() = runTest {
        ledger.inner.saveTransaction(tx("salary", TransactionType.INCOME, lkr(150_000), date = today))
        // USD 100 at the saved rate of 300 → LKR 30,000.
        ledger.inner.saveTransaction(tx("upwork", TransactionType.INCOME, usd(100), account = payoneer, date = today))
        ledger.inner.saveTransaction(
            tx("fiverr", TransactionType.INCOME, usd(40), account = payoneer, date = today, conversion = ReportingConversion.Missing(Currencies.LKR)),
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }

        val total = vm.uiState.value.receivedThisMonth!!
        assertEquals("LKR 150,000 + LKR 30,000; the USD 40 without a rate is left out", lkr(180_000), total.amount)
        assertEquals(1, total.unresolvedCount)
        val rows = vm.uiState.value.months.single().rows.associateBy { it.id }
        assertTrue(rows.getValue("fiverr").missingRate)
        assertFalse(rows.getValue("upwork").missingRate)
        assertEquals("Payoneer", rows.getValue("upwork").accountName)
        assertEquals("Shown in its own currency", usd(100), rows.getValue("upwork").amount)
        vm.viewModelScope.cancel()
    }

    @Test
    fun loadFailureCanBeRetried() = runTest {
        ledger.broken = true
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        assertTrue(vm.uiState.value.loadFailed)

        ledger.broken = false
        vm.retry()
        assertFalse(vm.uiState.value.loadFailed)
        assertFalse(vm.uiState.value.isLoading)
        vm.viewModelScope.cancel()
    }
}
