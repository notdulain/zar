package com.example.zar.features.transactions.presentation.activity

import com.example.zar.features.transactions.domain.category.usecase.ObserveExpenseCategoryCatalogUseCase
import com.example.zar.features.transactions.domain.category.FakeExpenseCategoryRepository
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.DatePreset
import com.example.zar.features.transactions.domain.activity.usecase.ObserveActivityUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityContent
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityUiState
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityViewModel
import java.time.LocalDate
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
class ActivityViewModelTest {
    private val ledger = FlakyLedger()
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val connectivity = FakeConnectivity()
    private val today: LocalDate = ZarTime.today()
    private val lastMonth: LocalDate = today.minusMonths(1).withDayOfMonth(15)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ActivityViewModel(
        observeActivity = ObserveActivityUseCase(ledger, accounts, ObserveExpenseCategoryCatalogUseCase(FakeExpenseCategoryRepository())),
        observeOnline = ObserveOnlineStatusUseCase(connectivity),
    )

    private fun expense(id: String, amount: Long, account: com.example.zar.core.domain.model.Account = cash, date: LocalDate = today): Transaction =
        tx(id, TransactionType.EXPENSE, lkr(amount), account, date).copy(merchant = id.replaceFirstChar { it.uppercase() })

    private fun ActivityUiState.ids() = (content as ActivityContent.Ready).list.rows.map { it.entry.id.value }

    /** Runs [block] with the state flow being collected, as it is on screen. */
    private fun runScreen(block: suspend kotlinx.coroutines.test.TestScope.(ActivityViewModel) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect { } }
            block(vm)
        }

    @Test
    fun startsLoadingThenShowsThisMonthsEntriesWithTheirTotals() = runScreen { vm ->
        ledger.save(
            expense("keells", 8_400),
            expense("old", 999, date = lastMonth),
            tx("pay", TransactionType.INCOME, lkr(125_000), bank, today),
        )

        val state = vm.uiState.value
        assertEquals(setOf("keells", "pay"), state.ids().toSet())
        val summary = (state.content as ActivityContent.Ready).list.summary
        assertEquals(lkr(125_000), summary.received.amount)
        assertEquals(lkr(8_400), summary.spent.total.amount)
    }

    @Test
    fun theListUpdatesWhenSomethingIsSaved() = runScreen { vm ->
        ledger.save(expense("a", 100))
        assertEquals(listOf("a"), vm.uiState.value.ids())

        ledger.save(expense("b", 200))

        assertEquals(setOf("a", "b"), vm.uiState.value.ids().toSet())
    }

    @Test
    fun filteringByAccountNarrowsTheListAndTheTotals() = runScreen { vm ->
        ledger.save(expense("cashy", 100, cash), expense("banky", 700, bank))

        vm.applyFilter(ActivityFilter(accountId = cash.id))

        val state = vm.uiState.value
        assertEquals(listOf("cashy"), state.ids())
        assertEquals(lkr(100), (state.content as ActivityContent.Ready).list.summary.spent.total.amount)
        assertEquals(1, state.filter.activeCount)
    }

    @Test
    fun aChosenRangeShowsEntriesFromThatRange() = runScreen { vm ->
        ledger.save(expense("now", 100), expense("old", 200, date = lastMonth))

        vm.applyFilter(ActivityFilter(preset = DatePreset.LAST_MONTH))

        assertEquals(listOf("old"), vm.uiState.value.ids())
    }

    @Test
    fun voidedEntriesAreHiddenUntilAskedFor() = runScreen { vm ->
        ledger.save(expense("kept", 100), expense("gone", 200))
        ledger.inner.void(listOf(VoidMarker(com.example.zar.core.domain.model.TransactionId("gone"), com.example.zar.core.domain.TestLedger.T0)))

        assertEquals(listOf("kept"), vm.uiState.value.ids())

        vm.applyFilter(ActivityFilter(showVoided = true))

        assertEquals(setOf("kept", "gone"), vm.uiState.value.ids().toSet())
        val summary = (vm.uiState.value.content as ActivityContent.Ready).list.summary
        assertEquals(lkr(100), summary.spent.total.amount)
    }

    @Test
    fun searchingKeepsAcrossFilterChangesAndClearingResetsEverything() = runScreen { vm ->
        ledger.save(expense("keells", 100), expense("pickme", 200))

        vm.setQuery("keel")
        assertEquals(listOf("keells"), vm.uiState.value.ids())

        vm.applyFilter(ActivityFilter(accountId = cash.id))
        assertEquals("keel", vm.uiState.value.filter.query)

        vm.clearFilters()
        assertEquals(ActivityFilter(), vm.uiState.value.filter)
        assertEquals(setOf("keells", "pickme"), vm.uiState.value.ids().toSet())
    }

    @Test
    fun theSheetCanPreviewHowManyEntriesAFilterWouldShow() = runScreen { vm ->
        ledger.save(expense("a", 1, cash), expense("b", 2, cash), expense("c", 3, bank))

        assertEquals(2, vm.previewCount(ActivityFilter(accountId = cash.id)))
        assertEquals(3, vm.previewCount(ActivityFilter()))
        assertEquals(0, vm.previewCount(ActivityFilter(preset = DatePreset.LAST_MONTH)))
    }

    @Test
    fun noEntriesIsReadyAndEmptyNotAnError() = runScreen { vm ->
        val list = (vm.uiState.value.content as ActivityContent.Ready).list

        assertTrue(list.rows.isEmpty())
        assertFalse(list.hasAnyEntries)
    }

    @Test
    fun aFailedLoadShowsAnErrorAndRetryRecovers() = runTest(UnconfinedTestDispatcher()) {
        ledger.loadFailures = 1
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        assertEquals(ActivityContent.Error, vm.uiState.value.content)

        ledger.save(expense("a", 100))
        vm.retry()

        assertEquals(listOf("a"), vm.uiState.value.ids())
    }

    @Test
    fun theOfflineFlagFollowsTheConnection() = runScreen { vm ->
        assertTrue(vm.uiState.value.isOnline)

        connectivity.online.value = false

        assertFalse(vm.uiState.value.isOnline)
        // What is stored on the phone is still listed while offline.
        ledger.save(expense("saved-offline", 100))
        assertEquals(listOf("saved-offline"), vm.uiState.value.ids())
    }

    @Test
    fun filterAccountsLeaveOutArchivedOnes() = runScreen { vm ->
        accounts.accounts.value = listOf(bank, cash.copy(isArchived = true))

        assertEquals(listOf(bank), vm.uiState.value.accounts)
    }
}
