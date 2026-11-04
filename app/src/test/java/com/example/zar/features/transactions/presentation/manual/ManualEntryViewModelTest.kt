package com.example.zar.features.transactions.presentation.manual

import com.example.zar.features.transactions.domain.manual.model.CategoryIcon
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.category.CategoryNameRules
import com.example.zar.features.transactions.domain.category.CategoryNameProblem
import com.example.zar.features.transactions.domain.category.usecase.CreateExpenseCategoryUseCase
import com.example.zar.features.transactions.domain.category.usecase.ObserveExpenseCategoryCatalogUseCase
import com.example.zar.features.transactions.domain.category.FakeExpenseCategoryRepository
import com.example.zar.features.rules.FakeCategoryRules
import com.example.zar.features.rules.rule
import com.example.zar.features.rules.newInbox
import androidx.lifecycle.SavedStateHandle
import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.ConnectivityMonitor
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.features.accounts.domain.usecase.ObserveActiveAccountsUseCase
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseProblem
import com.example.zar.features.transactions.domain.manual.model.TransferProblem
import com.example.zar.features.transactions.domain.manual.usecase.ObserveEntrySuggestionsUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.domain.manual.usecase.RecordExpenseUseCase
import com.example.zar.features.transactions.domain.manual.usecase.RecordTransferUseCase
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryMessage
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryViewModel
import java.io.IOException
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeAccounts(initial: List<Account>) : AccountRepository {
    val accounts = MutableStateFlow(initial)
    override fun observeAccounts(): Flow<List<Account>> = accounts
    override suspend fun saveAccount(account: Account) = Unit
}

private class FakeConnectivity(initial: Boolean = true) : ConnectivityMonitor {
    val online = MutableStateFlow(initial)
    override fun observeOnline(): Flow<Boolean> = online
}

/**
 * A ledger that keeps saved records by ID (write-once, like Firestore). [failAfterStoring] makes a
 * save store the record and then report an error, which is what a lost acknowledgement looks like.
 */
private class FakeEntryLedger : LedgerRepository {
    val saved = linkedMapOf<TransactionId, Transaction>()
    val entries = MutableStateFlow<List<LedgerEntry>>(emptyList())
    var failure: Throwable? = null
    var failAfterStoring = false
    var saveCalls = 0

    override fun observeLedger(): Flow<List<LedgerEntry>> = entries

    override suspend fun saveTransaction(transaction: Transaction) {
        saveCalls++
        failure?.takeUnless { failAfterStoring }?.let { throw it }
        saved.putIfAbsent(transaction.id, transaction)
        failure?.let { throw it }
    }

    override suspend fun saveTransfer(transfer: Transfer) {
        saveCalls++
        failure?.takeUnless { failAfterStoring }?.let { throw it }
        val records = listOf(transfer.outLeg(), transfer.inLeg()) + listOfNotNull(transfer.fee)
        if (records.none { it.id in saved }) records.forEach { saved[it.id] = it }
        failure?.let { throw it }
    }

    override suspend fun addCorrection(correction: Correction) = error("not used")
    override suspend fun void(markers: List<VoidMarker>) = error("not used")
    override suspend fun linkEvidence(link: EvidenceLink) = error("not used")
}

@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryViewModelTest {
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val ledger = FakeEntryLedger()
    private val connectivity = FakeConnectivity()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val rules = FakeCategoryRules()
    private val categoryRepo = FakeExpenseCategoryRepository()
    private val categoryCatalog = ObserveExpenseCategoryCatalogUseCase(categoryRepo)
    private val inbox = newInbox()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): ManualEntryViewModel {
        val saveTransaction = SaveTransactionUseCase(ledger)
        return ManualEntryViewModel(
            handle,
            observeAccounts = ObserveActiveAccountsUseCase(accounts),
            observeSuggestions = ObserveEntrySuggestionsUseCase(ledger, categoryCatalog),
            observeOnline = ObserveOnlineStatusUseCase(connectivity),
            recordExpense = RecordExpenseUseCase(saveTransaction),
            recordTransfer = RecordTransferUseCase(SaveTransferUseCase(ledger)),
            observeRules = rules.observeUseCase(),
            ruleInbox = inbox,
            observeCategories = categoryCatalog,
            createCategory = CreateExpenseCategoryUseCase(categoryRepo),
        )
    }

    private fun ManualEntryViewModel.fillValidExpense() {
        onAmountChange("850")
        onCategorySelect(ExpenseCategory.TRANSPORT)
    }

    private fun recentExpense(id: String, title: String, category: String, amount: Long = 850, account: Account = cash) {
        val tx = TestLedger.tx(id, TransactionType.EXPENSE, lkr(amount), account = account).copy(merchant = title, categoryId = category)
        ledger.entries.value = ledger.entries.value + TestLedger.entry(tx)
    }

    // --- Loading --------------------------------------------------------------------------

    @Test
    fun startsOnExpenseWithTheFirstAccountSelected() {
        accounts.accounts.value = listOf(bank)
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(ManualEntryKind.EXPENSE, state.kind)
        assertEquals(bank.id, state.accountId)
        assertEquals(SpendingClass.DISCRETIONARY, state.spendingClass)
        assertEquals("LKR", state.currency.code)
    }

    @Test
    fun transferStartsWithTwoDifferentAccountsOfTheSameCurrency() {
        accounts.accounts.value = listOf(bank, cash, payoneer)
        val state = viewModel().uiState.value

        assertNotNull(state.fromAccount)
        assertNotNull(state.toAccount)
        assertNotEquals(state.fromId, state.toId)
        assertEquals(state.fromAccount!!.currency, state.toAccount!!.currency)
    }

    @Test
    fun withNoAccountsNothingCanBeSaved() {
        accounts.accounts.value = emptyList()
        val vm = viewModel()
        vm.fillValidExpense()
        vm.save()

        assertTrue(vm.uiState.value.accounts.isEmpty())
        assertTrue(ExpenseProblem.ACCOUNT_MISSING in vm.uiState.value.expenseProblems)
        assertTrue(ledger.saved.isEmpty())
    }

    @Test
    fun accountLoadFailureStopsLoadingAndShowsAnError() {
        val failing = object : AccountRepository {
            override fun observeAccounts(): Flow<List<Account>> = flow { throw IOException("offline") }
            override suspend fun saveAccount(account: Account) = Unit
        }
        val vm = ManualEntryViewModel(
            SavedStateHandle(),
            ObserveActiveAccountsUseCase(failing),
            ObserveEntrySuggestionsUseCase(ledger, categoryCatalog),
            ObserveOnlineStatusUseCase(connectivity),
            RecordExpenseUseCase(SaveTransactionUseCase(ledger)),
            RecordTransferUseCase(SaveTransferUseCase(ledger)),
            rules.observeUseCase(),
            inbox,
            categoryCatalog,
            CreateExpenseCategoryUseCase(categoryRepo),
        )

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(ManualEntryMessage.FAILED, vm.uiState.value.message)
    }

    @Test
    fun anAccountThatDisappearsFallsBackToAnotherOne() {
        val vm = viewModel()
        vm.onAccountSelect(cash.id)

        accounts.accounts.value = listOf(bank)

        assertEquals(bank.id, vm.uiState.value.accountId)
    }

    @Test
    fun reportsWhetherThePhoneIsOnline() {
        val vm = viewModel()
        assertTrue(vm.uiState.value.isOnline)

        connectivity.online.value = false
        assertFalse(vm.uiState.value.isOnline)
    }

    // --- Expense --------------------------------------------------------------------------

    @Test
    fun savingAnEmptyExpenseShowsEveryMissingFieldAndSavesNothing() {
        val vm = viewModel()
        vm.save()

        val state = vm.uiState.value
        assertEquals(setOf(ExpenseProblem.AMOUNT_MISSING, ExpenseProblem.CATEGORY_MISSING), state.expenseProblems)
        assertFalse(state.isFinished)
        assertTrue(ledger.saved.isEmpty())
    }

    @Test
    fun editingAFieldClearsItsOwnProblem() {
        val vm = viewModel()
        vm.save()

        vm.onAmountChange("5")
        assertEquals(setOf(ExpenseProblem.CATEGORY_MISSING), vm.uiState.value.expenseProblems)

        vm.onCategorySelect(ExpenseCategory.OTHER)
        assertTrue(vm.uiState.value.expenseProblems.isEmpty())
    }

    @Test
    fun aValidExpenseIsSavedOnTheChosenAccountAndFinishesTheScreen() {
        val vm = viewModel()
        vm.onAccountSelect(cash.id)
        vm.fillValidExpense()
        vm.onTitleChange("Bus to Kandy")
        vm.onSpendingClassSelect(SpendingClass.COMMITTED)
        vm.save()

        val saved = ledger.saved.values.single()
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(cash.id, saved.accountId)
        assertEquals(lkr(850), saved.amount)
        assertEquals("transport", saved.categoryId)
        assertEquals("Bus to Kandy", saved.merchant)
        assertEquals(SpendingClass.COMMITTED, saved.spendingClass)
        assertTrue(vm.uiState.value.isFinished)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun selectedTimeReachesSavedExpense() {
        val vm = viewModel()
        vm.fillValidExpense()
        vm.onTimeSelect(LocalTime.of(9, 5, 35))
        vm.save()

        assertEquals(LocalTime.of(9, 5), vm.uiState.value.time)
        assertEquals(LocalTime.of(9, 5), ledger.saved.values.single().time)
    }

    @Test
    fun aFailedSaveKeepsTheFormAndRetryNeverDuplicatesTheEntry() {
        val vm = viewModel()
        vm.fillValidExpense()
        // The write lands but the acknowledgement is lost, so the user taps Save again.
        ledger.failure = IOException("timeout")
        ledger.failAfterStoring = true
        vm.save()

        assertEquals(ManualEntryMessage.FAILED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isFinished)
        assertEquals("850", vm.uiState.value.amount)

        ledger.failure = null
        vm.save()

        assertEquals(1, ledger.saved.size)
        assertTrue(vm.uiState.value.isFinished)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun notBeingSignedInIsReportedSeparately() {
        val vm = viewModel()
        vm.fillValidExpense()
        ledger.failure = LedgerError.NotSignedIn()
        vm.save()

        assertEquals(ManualEntryMessage.NOT_SIGNED_IN, vm.uiState.value.message)
        assertTrue(ledger.saved.isEmpty())
    }

    @Test
    fun aCategoryPresetsCountsAsUntilTheUserChoosesOne() {
        val vm = viewModel()

        vm.onCategorySelect(ExpenseCategory.RENT)
        assertEquals(SpendingClass.COMMITTED, vm.uiState.value.spendingClass)

        vm.onCategorySelect(ExpenseCategory.TRANSPORT)
        assertEquals("a category without a usual class keeps the current one", SpendingClass.COMMITTED, vm.uiState.value.spendingClass)

        vm.onSpendingClassSelect(SpendingClass.UNKNOWN)
        vm.onCategorySelect(ExpenseCategory.GROCERIES)
        assertEquals("the user's own choice wins", SpendingClass.UNKNOWN, vm.uiState.value.spendingClass)
    }

    // --- Quick note suggestions -----------------------------------------------------------

    @Test
    fun typingTheStartOfARecentTitleOffersItAndPickingFillsTheForm() {
        recentExpense("e1", "Bus to Kandy", "transport", amount = 850, account = bank)
        val vm = viewModel()

        vm.onTitleChange("bu")
        assertEquals(listOf("Bus to Kandy"), vm.uiState.value.suggestions.map { it.title })

        vm.onSuggestionSelect(vm.uiState.value.suggestions.single())
        val state = vm.uiState.value
        assertEquals("Bus to Kandy", state.title)
        assertEquals(ExpenseCategory.TRANSPORT, state.category)
        assertEquals(bank.id, state.accountId)
        assertEquals("850", state.amount)
        assertTrue(state.suggestions.isEmpty())
    }

    @Test
    fun aSuggestionNeverOverwritesAnAmountTheUserAlreadyTyped() {
        recentExpense("e1", "Bus to Kandy", "transport", amount = 850)
        val vm = viewModel()
        vm.onAmountChange("900")
        vm.onTitleChange("Bus")

        vm.onSuggestionSelect(vm.uiState.value.suggestions.single())

        assertEquals("900", vm.uiState.value.amount)
    }

    @Test
    fun anExactMatchIsNotSuggestedBackToTheUser() {
        recentExpense("e1", "Bus to Kandy", "transport")
        val vm = viewModel()

        vm.onTitleChange("bus to kandy")

        assertTrue(vm.uiState.value.suggestions.isEmpty())
    }

    // --- Transfer -------------------------------------------------------------------------

    @Test
    fun amountAndDateAreKeptWhenSwitchingBetweenExpenseAndTransfer() {
        val vm = viewModel()
        vm.onAmountChange("1200")

        vm.onKindSelect(ManualEntryKind.TRANSFER)

        assertEquals("1200", vm.uiState.value.amount)
        assertEquals(ManualEntryKind.TRANSFER, vm.uiState.value.kind)
    }

    @Test
    fun theSendingAccountAndOtherCurrenciesCannotBeChosenAsTheDestination() {
        accounts.accounts.value = listOf(bank, cash, payoneer)
        val vm = viewModel()
        vm.onFromSelect(bank.id)
        vm.onToSelect(cash.id)

        vm.onToSelect(bank.id)
        assertEquals(cash.id, vm.uiState.value.toId)

        vm.onToSelect(payoneer.id)
        assertEquals(cash.id, vm.uiState.value.toId)
        assertTrue(vm.uiState.value.hidesOtherCurrencies)
    }

    @Test
    fun pickingTheDestinationAsTheSenderMovesTheDestinationAway() {
        accounts.accounts.value = listOf(bank, cash)
        val vm = viewModel()
        vm.onFromSelect(bank.id)
        vm.onToSelect(cash.id)

        vm.onFromSelect(cash.id)

        assertEquals(cash.id, vm.uiState.value.fromId)
        assertEquals(bank.id, vm.uiState.value.toId)
    }

    @Test
    fun swappingExchangesTheTwoAccounts() {
        accounts.accounts.value = listOf(bank, cash)
        val vm = viewModel()
        vm.onFromSelect(bank.id)
        vm.onToSelect(cash.id)

        vm.onSwapAccounts()

        assertEquals(cash.id, vm.uiState.value.fromId)
        assertEquals(bank.id, vm.uiState.value.toId)
    }

    @Test
    fun theTransferPreviewAddsTheFeeToWhatLeavesTheSender() {
        val vm = viewModel()
        vm.onKindSelect(ManualEntryKind.TRANSFER)
        vm.onAmountChange("5000")
        vm.onFeeChange("30")

        assertEquals(lkr(5_030), vm.uiState.value.transferOutTotal)
        assertEquals(lkr(5_000), vm.uiState.value.transferInTotal)

        vm.onAmountChange("")
        assertNull(vm.uiState.value.transferOutTotal)
    }

    @Test
    fun aTransferWithOneAccountOnlyAsksForADestination() {
        accounts.accounts.value = listOf(bank)
        val vm = viewModel()
        vm.onKindSelect(ManualEntryKind.TRANSFER)
        vm.onAmountChange("5000")
        vm.save()

        assertEquals(setOf(TransferProblem.TO_MISSING), vm.uiState.value.transferProblems)
        assertTrue(ledger.saved.isEmpty())
    }

    @Test
    fun savingATransferStoresBothLegsAndTheFeeThenFinishes() {
        accounts.accounts.value = listOf(bank, cash)
        val vm = viewModel()
        vm.onKindSelect(ManualEntryKind.TRANSFER)
        vm.onFromSelect(bank.id)
        vm.onToSelect(cash.id)
        vm.onAmountChange("5000")
        vm.onFeeChange("30")
        vm.onTransferNoteChange("rent top-up")
        vm.save()

        val types = ledger.saved.values.groupBy { it.type }.mapValues { it.value.size }
        assertEquals(mapOf(TransactionType.TRANSFER_OUT to 1, TransactionType.TRANSFER_IN to 1, TransactionType.EXPENSE to 1), types)
        assertEquals(bank.id, ledger.saved.values.single { it.type == TransactionType.TRANSFER_OUT }.accountId)
        assertEquals(cash.id, ledger.saved.values.single { it.type == TransactionType.TRANSFER_IN }.accountId)
        assertEquals(lkr(30), ledger.saved.values.single { it.type == TransactionType.EXPENSE }.amount)
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun retryingATransferNeverDuplicatesAnyLeg() {
        accounts.accounts.value = listOf(bank, cash)
        val vm = viewModel()
        vm.onKindSelect(ManualEntryKind.TRANSFER)
        vm.onAmountChange("5000")
        vm.onFeeChange("30")
        ledger.failure = IOException("timeout")
        ledger.failAfterStoring = true
        vm.save()
        assertEquals(ManualEntryMessage.FAILED, vm.uiState.value.message)

        ledger.failure = null
        vm.save()

        assertEquals(3, ledger.saved.size)
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun aTransferIsNeverSavedAsAnExpenseOrIncome() {
        accounts.accounts.value = listOf(bank, cash)
        val vm = viewModel()
        vm.onKindSelect(ManualEntryKind.TRANSFER)
        vm.onAmountChange("5000")
        vm.save()

        assertEquals(
            setOf(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN),
            ledger.saved.values.map { it.type }.toSet(),
        )
    }
    @Test
    fun overflowInPreviewIsRejectedWithoutCrashing() {
        val vm = viewModel()
        vm.onKindSelect(ManualEntryKind.TRANSFER)
        vm.onAmountChange("92233720368547758.07")
        vm.onFeeChange("0.01")
        assertNull(vm.uiState.value.transferOutTotal)
        vm.save()
        assertEquals(setOf(TransferProblem.FEE_INVALID), vm.uiState.value.transferProblems)
        assertTrue(ledger.saved.isEmpty())
    }

    @Test
    fun archivedAccountSuggestionsCannotMoveForeignAmountsToAnotherAccount() {
        accounts.accounts.value = listOf(bank, payoneer)
        val expense = TestLedger.tx("usd-expense", TransactionType.EXPENSE, TestLedger.usd(12), account = payoneer)
            .copy(merchant = "Cab", categoryId = "transport")
        ledger.entries.value = listOf(TestLedger.entry(expense))
        val vm = viewModel()
        vm.onTitleChange("Ca")
        val suggestion = vm.uiState.value.suggestions.single()
        accounts.accounts.value = listOf(bank, payoneer.copy(isArchived = true))
        assertTrue(vm.uiState.value.suggestions.isEmpty())
        vm.onSuggestionSelect(suggestion)
        assertEquals(bank.id, vm.uiState.value.accountId)
        assertEquals("", vm.uiState.value.amount)
    }

    @Test
    fun saveIsClaimedBeforeTheCoroutineRunsAndCannotRepeatAfterFinishing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        runCurrent()
        vm.fillValidExpense()
        vm.save()
        vm.save()
        assertTrue(vm.uiState.value.isSaving)
        runCurrent()
        assertEquals(1, ledger.saveCalls)
        vm.save()
        runCurrent()
        assertEquals(1, ledger.saveCalls)
    }


    // --- Add the missing entry: opened from a balance check with the gap filled in -----------------

    private fun gapHandle(accountId: String = cash.id.value, amount: String = "450") =
        SavedStateHandle(mapOf("prefillAmount" to amount, "prefillAccountId" to accountId))

    @Test
    fun openedFromABalanceCheckTheGapAndAccountAreFilledAndTheAccountIsLocked() {
        val vm = viewModel(gapHandle())

        val state = vm.uiState.value
        assertEquals("450", state.amount)
        assertEquals("450", state.gapAmount)
        assertEquals(cash.id, state.accountId)
        assertTrue(state.accountLocked)
        assertEquals(ManualEntryKind.EXPENSE, state.kind)
    }

    @Test
    fun theLockedAccountAndTheExpenseKindCannotBeChangedWhileExplainingAGap() {
        val vm = viewModel(gapHandle())

        vm.onAccountSelect(bank.id)
        vm.onKindSelect(ManualEntryKind.TRANSFER)

        assertEquals(cash.id, vm.uiState.value.accountId)
        assertEquals(ManualEntryKind.EXPENSE, vm.uiState.value.kind)
    }

    @Test
    fun savingTheMissingExpenseRecordsItOnTheCheckedAccountForTheGap() {
        val vm = viewModel(gapHandle())
        vm.onCategorySelect(ExpenseCategory.GROCERIES)

        vm.save()

        assertTrue(vm.uiState.value.isFinished)
        val saved = ledger.saved.values.single()
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(cash.id, saved.accountId)
        assertEquals(lkr(450), saved.amount)
    }

    @Test
    fun theGapAmountCanStillBeEditedBeforeSaving() {
        val vm = viewModel(gapHandle())

        vm.onAmountChange("300")

        assertEquals("300", vm.uiState.value.amount)
        assertEquals("450", vm.uiState.value.gapAmount)
    }

    @Test
    fun ifTheCheckedAccountIsNoLongerAvailableTheFormIsAnOrdinaryOne() {
        val vm = viewModel(gapHandle(accountId = "archived-account"))

        val state = vm.uiState.value
        assertFalse(state.accountLocked)
        assertNull(state.gapAmount)
        vm.onAccountSelect(bank.id)
        assertEquals(bank.id, vm.uiState.value.accountId)
    }

    @Test
    fun aNoteCarriedOverFromQuickNoteFillsTheTitleAndNothingElse() {
        val vm = viewModel(SavedStateHandle(mapOf("prefillTitle" to "paid 1250 at keells super")))

        val state = vm.uiState.value
        assertEquals("paid 1250 at keells super", state.title)
        assertEquals("", state.amount)
        assertNull(state.category)
        assertFalse(state.accountLocked)
    }

    @Test
    fun anOrdinaryFormHasNoGapAndAnUnlockedAccount() {
        val state = viewModel().uiState.value

        assertNull(state.gapAmount)
        assertFalse(state.accountLocked)
        assertEquals("", state.amount)
    }

    // --- Category rules (SCRUM-39) ---

    @Test
    fun typingARememberedNameFillsTheCategoryAndSaysWhichRuleDidIt() {
        rules.rules.value = listOf(rule("Keells Super", category = "groceries", version = 2))
        val vm = viewModel()
        vm.onTitleChange("keells  super")

        val state = vm.uiState.value
        assertEquals(ExpenseCategory.GROCERIES, state.category)
        assertEquals(2, state.appliedRule?.version)
    }

    @Test
    fun aCategoryThePersonPickedIsNeverReplacedByARule() {
        rules.rules.value = listOf(rule("Keells Super", category = "groceries"))
        val vm = viewModel()
        vm.onCategorySelect(ExpenseCategory.COFFEE_DINING)
        vm.onTitleChange("Keells Super")

        assertEquals(ExpenseCategory.COFFEE_DINING, vm.uiState.value.category)
        assertNull(vm.uiState.value.appliedRule)
    }

    @Test
    fun choosingAnotherCategoryAfterTheRuleFilledItDropsTheRule() {
        rules.rules.value = listOf(rule("Keells Super", category = "groceries"))
        val vm = viewModel()
        vm.onTitleChange("Keells Super")
        vm.onCategorySelect(ExpenseCategory.OTHER)

        assertNull(vm.uiState.value.appliedRule)
        vm.onAmountChange("850")
        vm.save()
        assertNull(ledger.saved.values.single().appliedRuleId)
    }

    @Test
    fun theSavedExpenseRemembersTheRuleAndVersionThatFiledIt() {
        rules.rules.value = listOf(rule("Keells Super", category = "groceries", version = 3))
        val vm = viewModel()
        vm.onAmountChange("850")
        vm.onTitleChange("Keells Super")
        vm.save()

        val saved = ledger.saved.values.single()
        assertEquals("groceries", saved.categoryId)
        assertEquals("r-keells-super", saved.appliedRuleId)
        assertEquals(3, saved.appliedRuleVersion)
        assertEquals(lkr(850), saved.amount)
    }

    @Test
    fun aRuleNeverChangesTheCountsAsChoice() {
        rules.rules.value = listOf(rule("Keells Super", category = "groceries"))
        val vm = viewModel()
        vm.onSpendingClassSelect(SpendingClass.COMMITTED)
        vm.onTitleChange("Keells Super")

        assertEquals(SpendingClass.COMMITTED, vm.uiState.value.spendingClass)
    }

    @Test
    fun aGeneralNameIsNotFilledEvenIfARuleForItExists() {
        rules.rules.value = listOf(rule("PickMe", category = "transport"))
        val vm = viewModel()
        vm.onTitleChange("PickMe")

        assertNull(vm.uiState.value.category)
        assertNull(vm.uiState.value.appliedRule)
    }

    @Test
    fun disagreeingRulesFillNothing() {
        rules.rules.value = listOf(
            rule("Keells Super", category = "groceries", id = "a"),
            rule("Keells Super", category = "coffee-dining", id = "b"),
        )
        val vm = viewModel()
        vm.onTitleChange("Keells Super")

        assertNull(vm.uiState.value.category)
    }

    @Test
    fun aRuleForOneAccountOnlyFillsWhenThatAccountIsPicked() {
        rules.rules.value = listOf(rule("Keells Super", category = "groceries", account = cash.id))
        val vm = viewModel()
        vm.onAccountSelect(bank.id)
        vm.onTitleChange("Keells Super")
        assertNull(vm.uiState.value.category)

        vm.onAccountSelect(cash.id)
        assertEquals(ExpenseCategory.GROCERIES, vm.uiState.value.category)
    }

    @Test
    fun aCategoryTheRuleFilledIsTakenBackWhenTheNameNoLongerMatches() {
        rules.rules.value = listOf(rule("Keells Super", category = "groceries"))
        val vm = viewModel()
        vm.onTitleChange("Keells Super")
        assertEquals(ExpenseCategory.GROCERIES, vm.uiState.value.category)

        vm.onTitleChange("Keells Super Kandy")

        assertNull(vm.uiState.value.category)
        assertNull(vm.uiState.value.appliedRule)
    }

    @Test
    fun savingAPreciseNamePostsAnOfferToRemember() {
        val vm = viewModel()
        vm.onAmountChange("850")
        vm.onCategorySelect(ExpenseCategory.GROCERIES)
        vm.onTitleChange("Keells Super")
        vm.save()

        val offer = inbox.state.value.offer
        assertEquals("keells super", offer?.merchantKey)
        assertEquals("groceries", offer?.categoryId)
        assertFalse(inbox.state.value.sheetOpen)
        assertTrue(rules.rules.value.isEmpty())
    }

    @Test
    fun noOfferForAGeneralNameABlankNameOrAChoiceARuleAlreadyCovers() {
        val general = viewModel()
        general.fillValidExpense()
        general.onTitleChange("PickMe")
        general.save()
        assertNull(inbox.state.value.offer)

        val blank = viewModel()
        blank.fillValidExpense()
        blank.save()
        assertNull(inbox.state.value.offer)

        rules.rules.value = listOf(rule("Keells Super", category = "groceries"))
        val covered = viewModel()
        covered.onAmountChange("850")
        covered.onTitleChange("Keells Super")
        covered.save()
        assertNull(inbox.state.value.offer)
    }

    @Test
    fun aFailedSaveOffersNothing() {
        ledger.failure = IOException("offline")
        val vm = viewModel()
        vm.fillValidExpense()
        vm.onTitleChange("Keells Super")
        vm.save()

        assertNull(inbox.state.value.offer)
    }

    @Test
    fun aTransferNeverOffersARule() {
        val vm = viewModel()
        vm.onKindSelect(ManualEntryKind.TRANSFER)
        vm.onAmountChange("500")
        vm.save()

        assertNull(inbox.state.value.offer)
    }

    // --- Custom expense categories ---

    private fun makeCategory(vm: ManualEntryViewModel, name: String) {
        vm.onAddCategoryStart()
        vm.onNewCategoryNameChange(name)
        vm.onAddCategorySave()
    }

    @Test
    fun creatingACategorySelectsItAndKeepsTheWholeDraft() {
        val vm = viewModel()
        vm.onAccountSelect(cash.id)
        vm.onAmountChange("850")
        vm.onTitleChange("Vet")
        vm.onCategorySelect(ExpenseCategory.TRANSPORT)

        makeCategory(vm, " Pets ")

        val state = vm.uiState.value
        val created = state.categoryCatalog.customs.single()
        assertEquals("Pets", created.name)
        assertEquals(created, state.category)
        assertNull("form closes", state.newCategory)
        assertEquals("850", state.amount)
        assertEquals("Vet", state.title)
        assertEquals(cash.id, state.accountId)
        assertTrue("category is in the compact choices", created in state.categoryCatalog.choices)
        assertTrue("no transaction was saved by creating a category", ledger.saved.isEmpty())
        assertFalse(state.isFinished)
    }

    @Test
    fun cancelLeavesTheSelectionAndCreatesNothing() {
        val vm = viewModel()
        vm.onCategorySelect(ExpenseCategory.BILLS)
        vm.onAddCategoryStart()
        vm.onNewCategoryNameChange("Pets")
        vm.onAddCategoryCancel()

        assertEquals(ExpenseCategory.BILLS, vm.uiState.value.category)
        assertNull(vm.uiState.value.newCategory)
        assertEquals(0, categoryRepo.writes)
    }

    @Test
    fun nameProblemsStayInlineAndKeepTheTypedName() {
        val vm = viewModel()
        vm.onCategorySelect(ExpenseCategory.BILLS)
        listOf("" to CategoryNameProblem.EMPTY, "x".repeat(41) to CategoryNameProblem.TOO_LONG, " groceries " to CategoryNameProblem.DUPLICATE).forEach { (name, problem) ->
            makeCategory(vm, name)
            val form = vm.uiState.value.newCategory!!
            assertEquals(problem, form.problem)
            assertEquals(name, form.name)
            assertFalse(form.isSaving)
            assertFalse(form.failed)
        }
        assertEquals(ExpenseCategory.BILLS, vm.uiState.value.category)
        assertEquals(0, categoryRepo.writes)
        // Fixing the name clears the message and succeeds.
        vm.onNewCategoryNameChange("Pets")
        assertNull(vm.uiState.value.newCategory!!.problem)
        vm.onAddCategorySave()
        assertEquals("Pets", (vm.uiState.value.category as CustomExpenseCategory).name)
    }

    @Test
    fun aNewCategoryUsesTheDefaultIconUntilOneIsPicked() {
        val vm = viewModel()
        vm.onAddCategoryStart()
        assertEquals(CategoryIcon.DEFAULT, vm.uiState.value.newCategory!!.icon)
        vm.onNewCategoryNameChange("Pets")
        vm.onAddCategorySave()
        assertEquals(CategoryIcon.DEFAULT, vm.uiState.value.categoryCatalog.customs.single().icon)
    }

    @Test
    fun thePickedIconIsSavedWithTheCategoryAndKeptOnRetry() {
        val vm = viewModel()
        vm.onAddCategoryStart()
        vm.onNewCategoryNameChange("Pets")
        vm.onNewCategoryIconSelect(CategoryIcon.PIGGY_BANK)
        categoryRepo.failNextWith = java.io.IOException("unavailable")
        vm.onAddCategorySave()
        // A failed write keeps the form, the typed name and the picked icon.
        assertEquals(CategoryIcon.PIGGY_BANK, vm.uiState.value.newCategory!!.icon)
        // Editing the name does not reset the icon.
        vm.onNewCategoryNameChange("Pets and vet")
        assertEquals(CategoryIcon.PIGGY_BANK, vm.uiState.value.newCategory!!.icon)
        vm.onAddCategorySave()
        val created = vm.uiState.value.categoryCatalog.customs.single()
        assertEquals(CategoryIcon.PIGGY_BANK, created.icon)
        assertEquals(created, vm.uiState.value.category)
    }

    @Test
    fun pickingAnIconWithNoFormOpenChangesNothing() {
        val vm = viewModel()
        vm.onNewCategoryIconSelect(CategoryIcon.CAR)
        assertNull(vm.uiState.value.newCategory)
    }

    @Test
    fun aFailedWriteKeepsTheFormAndRetryingMakesOneCategory() {
        val vm = viewModel()
        vm.onCategorySelect(ExpenseCategory.BILLS)
        categoryRepo.failNextWith = java.io.IOException("unavailable")
        makeCategory(vm, "Pets")

        assertTrue(vm.uiState.value.newCategory!!.failed)
        assertEquals("Pets", vm.uiState.value.newCategory!!.name)
        assertEquals(ExpenseCategory.BILLS, vm.uiState.value.category)

        vm.onAddCategorySave()
        vm.onAddCategorySave()
        assertEquals(1, categoryRepo.writes)
        assertEquals(1, vm.uiState.value.categoryCatalog.customs.size)
        assertEquals("Pets", (vm.uiState.value.category as CustomExpenseCategory).name)
    }

    @Test
    fun aCategoryTheServerRefusesLaterIsTakenBackAndTheTypedNameCanBeRetried() {
        val vm = viewModel()
        vm.onAccountSelect(cash.id)
        vm.onAmountChange("850")
        vm.onTitleChange("Vet")
        makeCategory(vm, "Pets")
        val id = vm.uiState.value.categoryCatalog.customs.single().id

        categoryRepo.rejectLate(id)

        // Not a silent disappearance: the selection is released and the form reopens with the name, ready to retry.
        val state = vm.uiState.value
        assertNull(state.category)
        assertTrue(state.categoryCatalog.customs.isEmpty())
        assertTrue(state.newCategory!!.failed)
        assertEquals("Pets", state.newCategory!!.name)
        // The rest of the draft is untouched.
        assertEquals("850", state.amount)
        assertEquals("Vet", state.title)
        assertEquals(cash.id, state.accountId)

        // Retrying reuses the same ID, so the category comes back once under the id the draft already used.
        vm.onAddCategorySave()
        assertEquals(id, vm.uiState.value.categoryCatalog.customs.single().id)
        assertEquals(id, vm.uiState.value.category!!.id)
        assertNull(vm.uiState.value.newCategory)
    }

    @Test
    fun aLateRefusalOfACategoryThePersonHasSinceReplacedChangesNothing() {
        val vm = viewModel()
        makeCategory(vm, "Pets")
        val id = vm.uiState.value.categoryCatalog.customs.single().id
        vm.onCategorySelect(ExpenseCategory.BILLS)

        categoryRepo.rejectLate(id)

        assertEquals(ExpenseCategory.BILLS, vm.uiState.value.category)
        assertNull(vm.uiState.value.newCategory)
    }

    @Test
    fun anExpenseSavedUnderACustomCategoryStoresItsStableIdAndChosenClass() {
        val vm = viewModel()
        vm.onAccountSelect(cash.id)
        vm.onAmountChange("850")
        makeCategory(vm, "Pets")
        val id = vm.uiState.value.categoryCatalog.customs.single().id
        // A custom category has no usual class, so "Counts as" stays as it was; an explicit choice is kept.
        assertEquals(SpendingClass.DISCRETIONARY, vm.uiState.value.spendingClass)
        vm.onSpendingClassSelect(SpendingClass.COMMITTED)
        vm.save()

        val saved = ledger.saved.values.single()
        assertEquals(id, saved.categoryId)
        assertEquals(SpendingClass.COMMITTED, saved.spendingClass)
        assertTrue(CustomCategoryIds.isCustom(saved.categoryId))
    }

    @Test
    fun aSavedCustomCategoryIsSuggestedAndFilledByARule() {
        val id = "custom-9f8e7d6c-0000"
        categoryRepo.signInAs("alice")
        kotlinx.coroutines.runBlocking {
            categoryRepo.create(CustomExpenseCategory(id, "Pets", CategoryNameRules.keyOf("Pets"), java.time.Instant.EPOCH))
        }
        recentExpense("vet1", "Vet", id)
        rules.rules.value = listOf(rule("Pet Shop", category = id))
        val vm = viewModel()

        vm.onTitleChange("Ve")
        assertEquals(id, vm.uiState.value.suggestions.single().category?.id)
        vm.onTitleChange("pet shop")
        assertEquals(id, vm.uiState.value.category?.id)
        assertNotNull(vm.uiState.value.appliedRule)
    }

    @Test
    fun anotherAccountDoesNotSeeTheFirstAccountsCategories() {
        val vm = viewModel()
        makeCategory(vm, "Pets")
        categoryRepo.signInAs("bob")
        assertTrue(vm.uiState.value.categoryCatalog.customs.isEmpty())
        categoryRepo.signInAs("alice")
        assertEquals(1, vm.uiState.value.categoryCatalog.customs.size)
    }
}
