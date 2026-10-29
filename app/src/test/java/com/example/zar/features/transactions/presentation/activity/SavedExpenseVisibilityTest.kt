package com.example.zar.features.transactions.presentation.activity

import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.core.domain.usecase.DismissRejectedLedgerWriteUseCase
import com.example.zar.core.domain.usecase.ObserveRejectedLedgerWritesUseCase
import com.example.zar.core.domain.usecase.RetryRejectedLedgerWriteUseCase
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.features.home.domain.usecase.ObserveHomeOverviewUseCase
import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.DatePreset
import com.example.zar.features.transactions.domain.activity.usecase.ObserveActivityUseCase
import com.example.zar.features.transactions.domain.activity.usecase.ObserveEntryDetailUseCase
import com.example.zar.features.transactions.domain.category.FakeExpenseCategoryRepository
import com.example.zar.features.transactions.domain.category.usecase.ObserveExpenseCategoryCatalogUseCase
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseDetails
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.domain.manual.usecase.RecordExpenseUseCase
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityContent
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Saves through the same use case the manual form calls and reads back through the Activity and Home
 * use cases, so no step of the save-to-screen path is mocked except storage itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedExpenseVisibilityTest {
    private val ledger = FlakyLedger()
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val today: LocalDate = ZarTime.today()
    private val recordExpense = RecordExpenseUseCase(SaveTransactionUseCase(ledger))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun details(account: Account = cash, amount: Long = 500, date: LocalDate = today) = ExpenseDetails(
        account = account,
        amount = lkr(amount),
        date = date,
        category = ExpenseCategory.GROCERIES,
        spendingClass = ExpenseCategory.GROCERIES.usualClass!!,
        title = "Keells",
        description = null,
        time = LocalTime.of(13, 5),
    )

    private suspend fun saveExpense(id: String = "exp", details: ExpenseDetails = details()) =
        recordExpense(TransactionId(id), details, Instant.now()).getOrThrow()

    private suspend fun saveIncome(amount: Long = 1_250) =
        ledger.save(tx("inc", TransactionType.INCOME, lkr(amount), bank, today))

    private fun runScreen(block: suspend kotlinx.coroutines.test.TestScope.(ActivityViewModel) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            val vm = ActivityViewModel(
                observeActivity = ObserveActivityUseCase(
                    ledger,
                    accounts,
                    ObserveExpenseCategoryCatalogUseCase(FakeExpenseCategoryRepository()),
                ),
                observeOnline = ObserveOnlineStatusUseCase(FakeConnectivity()),
                observeRejected = ObserveRejectedLedgerWritesUseCase(ledger),
                retryRejected = RetryRejectedLedgerWriteUseCase(ledger),
                dismissRejected = DismissRejectedLedgerWriteUseCase(ledger),
            )
            backgroundScope.launch { vm.uiState.collect { } }
            block(vm)
        }

    private fun ActivityViewModel.list() = (uiState.value.content as ActivityContent.Ready).list

    @Test
    fun anExpenseSavedFromTheFormAppearsOnceWithTheTwoEntryTotalsAndMatchingHomeSummary() = runScreen { vm ->
        saveIncome()
        saveExpense()

        val list = vm.list()
        assertEquals(setOf("inc", "exp"), list.rows.map { it.entry.id.value }.toSet())
        assertEquals(2, list.rows.size)
        assertEquals(lkr(1_250), list.summary.received.amount)
        assertEquals(lkr(500), list.summary.spent.total.amount)

        val saved = list.rows.single { it.entry.id.value == "exp" }.entry.current
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(lkr(500), saved.amount)
        assertEquals("Keells", saved.merchant)
        assertEquals(cash.id, saved.accountId)
        assertEquals(ExpenseCategory.GROCERIES.id, saved.categoryId)

        val detail = ObserveEntryDetailUseCase(ledger, accounts)(TransactionId("exp")).first()!!
        assertEquals(saved, detail.entry.current)

        val home = ObserveHomeOverviewUseCase(ledger)(YearMonth.from(today)).first()
        assertEquals(list.summary.received.amount, home.summary.received.amount)
        assertEquals(list.summary.spent.total.amount, home.summary.spent.total.amount)
    }

    @Test
    fun retryingTheSameSaveKeepsOneRowAndOneCount() = runScreen { vm ->
        saveExpense()
        saveExpense()
        assertEquals(1, vm.list().rows.size)
        assertEquals(lkr(500), vm.list().summary.spent.total.amount)
    }

    @Test
    fun aPriorMonthExpenseIsOutOfThisMonthAndAppearsOnceInItsOwnMonth() = runScreen { vm ->
        saveExpense(details = details(date = today.minusMonths(1).withDayOfMonth(15)))

        assertTrue(vm.list().rows.isEmpty())
        assertTrue(vm.list().hasAnyEntries) // filtered no-results, not a true empty ledger
        vm.applyFilter(ActivityFilter(preset = DatePreset.LAST_MONTH))
        assertEquals(listOf("exp"), vm.list().rows.map { it.entry.id.value })
    }

    @Test
    fun anExpenseOnAnotherAccountIsHiddenByTheAccountFilterUntilFiltersAreCleared() = runScreen { vm ->
        saveExpense(details = details(account = cash))
        vm.applyFilter(ActivityFilter(accountId = bank.id))

        assertTrue(vm.list().rows.isEmpty())
        assertTrue(vm.list().hasAnyEntries)
        vm.clearFilters()
        assertEquals(listOf("exp"), vm.list().rows.map { it.entry.id.value })
    }

    @Test
    fun anExpenseOnAnAccountWithNoMetadataStaysVisibleInAllAccounts() = runScreen { vm ->
        saveExpense(details = details(account = cash))
        accounts.accounts.value = listOf(bank) // the expense's account is gone from the account list

        val row = vm.list().rows.single()
        assertEquals("exp", row.entry.id.value)
        assertNull(row.account)
        assertEquals(lkr(500), vm.list().summary.spent.total.amount)
    }

    @Test
    fun anExpenseOnAnArchivedAccountStaysVisibleInAllAccounts() = runScreen { vm ->
        saveExpense(details = details(account = cash))
        accounts.accounts.value = listOf(bank, cash.copy(isArchived = true))

        assertEquals(listOf("exp"), vm.list().rows.map { it.entry.id.value })
        assertEquals(cash.id, vm.list().rows.single().account?.id)
        assertTrue(vm.uiState.value.accounts.none { it.id == cash.id }) // not offered as a filter choice
    }

    @Test
    fun anExpenseWithUnknownConversionStaysVisibleWithItsOriginalAmountAndIsCountedAsUnresolved() = runScreen { vm ->
        accounts.accounts.value = listOf(bank, payoneer)
        saveExpense(id = "usd", details = details(account = payoneer, amount = 10).copy(amount = usd(10)))

        val row = vm.list().rows.single()
        assertEquals(usd(10), row.entry.current.amount)
        assertEquals(ReportingConversion.Missing(com.example.zar.core.domain.model.money.Currencies.LKR), row.entry.current.reportingConversion)
        assertEquals(1, vm.list().summary.spent.total.unresolvedCount)
        assertTrue(vm.list().summary.spent.total.amount.isZero)
    }

    @Test
    fun aVoidedExpenseLeavesTheTotalsButCanBeShown() = runScreen { vm ->
        saveExpense()
        ledger.inner.void(listOf(com.example.zar.core.domain.TestLedger.void("exp")))

        assertTrue(vm.list().rows.isEmpty())
        assertTrue(vm.list().summary.spent.total.amount.isZero)
        vm.applyFilter(ActivityFilter(showVoided = true))
        assertEquals(listOf("exp"), vm.list().rows.map { it.entry.id.value })
    }
}
