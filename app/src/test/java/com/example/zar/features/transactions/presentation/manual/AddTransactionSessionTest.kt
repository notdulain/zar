package com.example.zar.features.transactions.presentation.manual

import androidx.lifecycle.SavedStateHandle
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.usecase.CorrectTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.features.accounts.domain.usecase.ObserveActiveAccountsUseCase
import com.example.zar.features.rules.FakeCategoryRules
import com.example.zar.features.rules.newInbox
import com.example.zar.features.transactions.domain.category.FakeExpenseCategoryRepository
import com.example.zar.features.transactions.domain.category.usecase.CreateExpenseCategoryUseCase
import com.example.zar.features.transactions.domain.category.usecase.ObserveExpenseCategoryCatalogUseCase
import com.example.zar.features.transactions.domain.income.usecase.EditIncomeUseCase
import com.example.zar.features.transactions.domain.income.usecase.ObserveIncomeAccountsUseCase
import com.example.zar.features.transactions.domain.income.usecase.ObserveIncomeEntryUseCase
import com.example.zar.features.transactions.domain.income.usecase.RecordIncomeUseCase
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.usecase.ObserveEntrySuggestionsUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.domain.manual.usecase.RecordExpenseUseCase
import com.example.zar.features.transactions.domain.manual.usecase.RecordTransferUseCase
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormMessage
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormViewModel
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class SessionAccounts(initial: List<com.example.zar.core.domain.model.Account>) :
    com.example.zar.core.domain.repository.AccountRepository {
    val accounts = MutableStateFlow(initial)
    override fun observeAccounts(): Flow<List<com.example.zar.core.domain.model.Account>> = accounts
    override suspend fun saveAccount(account: com.example.zar.core.domain.model.Account) = Unit
}

private class SessionConnectivity : com.example.zar.core.domain.repository.ConnectivityMonitor {
    override fun observeOnline(): Flow<Boolean> = MutableStateFlow(true)
}

/**
 * The one Add-transaction session: Expense/Income/Transfer are tabs of a single sheet whose drafts must survive
 * every switch, and nothing reaches the ledger until Save is pressed on the active tab. These tests drive the two
 * real ViewModels that back the session (the manual engine for Expense/Transfer, the income engine for Income) over
 * one shared fake ledger, which is exactly how [com.example.zar.features.transactions.presentation.manual.screen.AddTransactionScreen]
 * hosts them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionSessionTest {
    private val accounts = SessionAccounts(listOf(TestLedger.bank, TestLedger.cash))
    private val ledger = FakeLedgerRepository()
    private val categoryRepo = FakeExpenseCategoryRepository()
    private val rules = FakeCategoryRules()
    private val inbox = newInbox()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun manual(handle: SavedStateHandle = SavedStateHandle()): ManualEntryViewModel {
        val categoryCatalog = ObserveExpenseCategoryCatalogUseCase(categoryRepo)
        val saveTransaction = SaveTransactionUseCase(ledger)
        return ManualEntryViewModel(
            handle,
            observeAccounts = ObserveActiveAccountsUseCase(accounts),
            observeSuggestions = ObserveEntrySuggestionsUseCase(ledger, categoryCatalog),
            observeOnline = ObserveOnlineStatusUseCase(SessionConnectivity()),
            recordExpense = RecordExpenseUseCase(saveTransaction),
            recordTransfer = RecordTransferUseCase(com.example.zar.core.domain.usecase.SaveTransferUseCase(ledger)),
            observeRules = rules.observeUseCase(),
            ruleInbox = inbox,
            observeCategories = categoryCatalog,
            createCategory = CreateExpenseCategoryUseCase(categoryRepo),
        )
    }

    private fun income(): IncomeFormViewModel {
        val saveTransaction = SaveTransactionUseCase(ledger)
        return IncomeFormViewModel(
            SavedStateHandle(),
            observeAccounts = ObserveIncomeAccountsUseCase(accounts),
            observeEntry = ObserveIncomeEntryUseCase(ledger),
            recordIncome = RecordIncomeUseCase(saveTransaction),
            editIncome = EditIncomeUseCase(CorrectTransactionUseCase(ledger, accounts)),
            voidTransaction = VoidTransactionUseCase(ledger),
        )
    }

    @Test
    fun switchingBetweenTabsKeepsEachDraftAndWritesNothing() {
        val expense = manual()
        val income = income()

        expense.onAmountChange("850")
        expense.onCategorySelect(ExpenseCategory.TRANSPORT)
        expense.onTitleChange("Bus to Kandy")

        income.onAmountChange("12,000")
        income.onReceivedFromChange("Upwork client")

        // Switch the manual engine to Transfer and back, exactly as tapping the tabs does.
        expense.onKindSelect(ManualEntryKind.TRANSFER)
        expense.onKindSelect(ManualEntryKind.EXPENSE)

        assertEquals("850", expense.uiState.value.amount)
        assertEquals(ExpenseCategory.TRANSPORT, expense.uiState.value.category)
        assertEquals("Bus to Kandy", expense.uiState.value.title)
        assertEquals("12,000", income.uiState.value.amount)
        assertEquals("Upwork client", income.uiState.value.receivedFrom)
        assertTrue("no ledger write before Save", ledger.transactions.isEmpty())
        assertFalse(expense.uiState.value.isFinished)
        assertFalse(income.uiState.value.isFinished)
    }

    @Test
    fun savingTheIncomeTabWritesExactlyOneIncomeAndLeavesTheExpenseDraftIntact() {
        val expense = manual()
        val income = income()
        expense.onAmountChange("850")
        expense.onCategorySelect(ExpenseCategory.TRANSPORT)
        income.onAmountChange("12000")
        income.onCategorySelect(com.example.zar.features.transactions.domain.income.model.IncomeCategory.FREELANCE)

        income.save()

        assertEquals(1, ledger.transactions.size)
        assertEquals(TransactionType.INCOME, ledger.transactions.values.single().type)
        assertTrue(income.uiState.value.isFinished)
        // The expense draft is untouched and unsaved.
        assertEquals("850", expense.uiState.value.amount)
        assertFalse(expense.uiState.value.isFinished)
    }

    @Test
    fun savingTheExpenseTabWritesExactlyOneExpenseAndLeavesTheIncomeDraftIntact() {
        val expense = manual()
        val income = income()
        income.onAmountChange("12000")
        expense.onAmountChange("850")
        expense.onCategorySelect(ExpenseCategory.TRANSPORT)

        expense.save()

        assertEquals(1, ledger.transactions.size)
        assertEquals(TransactionType.EXPENSE, ledger.transactions.values.single().type)
        assertTrue(expense.uiState.value.isFinished)
        assertEquals("12000", income.uiState.value.amount)
        assertFalse(income.uiState.value.isFinished)
    }

    @Test
    fun aTransferTabWritesBothLegsAndTheFeeInOneSave() {
        val expense = manual()
        expense.onKindSelect(ManualEntryKind.TRANSFER)
        expense.onFromSelect(TestLedger.bank.id)
        expense.onToSelect(TestLedger.cash.id)
        expense.onAmountChange("5000")
        expense.onFeeChange("30")

        expense.save()

        val types = ledger.transactions.values.groupBy { it.type }.mapValues { it.value.size }
        assertEquals(
            mapOf(TransactionType.TRANSFER_OUT to 1, TransactionType.TRANSFER_IN to 1, TransactionType.EXPENSE to 1),
            types,
        )
    }

    @Test
    fun aFailedIncomeSaveKeepsBothDraftsAndRetryWritesOne() {
        val expense = manual()
        val income = income()
        expense.onAmountChange("850")
        expense.onCategorySelect(ExpenseCategory.TRANSPORT)
        income.onAmountChange("12000")

        // Make the next income write fail before storing.
        ledger.failNext = IOException("offline")
        income.save()
        assertEquals(IncomeFormMessage.FAILED, income.uiState.value.message)
        assertEquals("12000", income.uiState.value.amount)
        assertEquals("850", expense.uiState.value.amount)
        assertFalse(income.uiState.value.isFinished)

        income.save()
        assertEquals(1, ledger.transactions.size)
        assertEquals(TransactionType.INCOME, ledger.transactions.values.single().type)
        assertTrue(income.uiState.value.isFinished)
    }

    @Test
    fun openingOnTransferStartsTheManualEngineOnTransfer() {
        val expense = manual(SavedStateHandle(mapOf("initialKind" to "transfer")))
        assertEquals(ManualEntryKind.TRANSFER, expense.uiState.value.kind)
    }
}
