package com.example.zar.features.transactions.presentation.activity

import androidx.lifecycle.SavedStateHandle
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.usecase.CorrectTransactionUseCase
import com.example.zar.features.transactions.domain.activity.EditProblem
import com.example.zar.features.transactions.domain.activity.usecase.CorrectExpenseUseCase
import com.example.zar.features.transactions.domain.activity.usecase.ObserveEntryDetailUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.presentation.activity.viewmodel.EditEntryViewModel
import com.example.zar.features.transactions.presentation.activity.viewmodel.EditMessage
import java.io.IOException
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditEntryViewModelTest {
    private val ledger = FlakyLedger()
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val connectivity = FakeConnectivity()
    private val today = ZarTime.today()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String = "e1") = EditEntryViewModel(
        savedStateHandle = SavedStateHandle(mapOf("entryId" to id)),
        observeDetail = ObserveEntryDetailUseCase(ledger, accounts),
        observeOnline = ObserveOnlineStatusUseCase(connectivity),
        correctExpense = CorrectExpenseUseCase(CorrectTransactionUseCase(ledger)),
    )

    private suspend fun saveExpense(id: String = "e1") {
        ledger.save(
            tx(id, TransactionType.EXPENSE, lkr(8_400), cash, today, SpendingClass.DISCRETIONARY)
                .copy(merchant = "Keells Super", note = "Weekly groceries", categoryId = "groceries"),
        )
    }

    private suspend fun entry(id: String = "e1"): LedgerEntry = ledger.observeLedger().first().first { it.id.value == id }

    @Test
    fun theFormStartsFromTheEntrysCurrentValues() = runTest(UnconfinedTestDispatcher()) {
        saveExpense()
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.notEditable)
        assertEquals("8400", state.input!!.amount)
        assertEquals("groceries", state.input!!.categoryId)
        assertEquals("Keells Super", state.input!!.title)
        assertEquals("LKR", state.currencyCode)
        assertEquals(cash, state.account)
    }

    @Test
    fun entriesThatCannotBeCorrectedHereAreMarkedNotEditable() = runTest(UnconfinedTestDispatcher()) {
        ledger.save(tx("inc", TransactionType.INCOME, lkr(1_000), bank, today))
        ledger.save(tx("gone", TransactionType.EXPENSE, lkr(10), cash, today))
        ledger.inner.void(listOf(VoidMarker(TransactionId("gone"), com.example.zar.core.domain.TestLedger.T0)))

        assertTrue(viewModel("inc").uiState.value.notEditable)
        assertTrue(viewModel("gone").uiState.value.notEditable)
        assertTrue(viewModel("missing").uiState.value.notEditable)
    }

    @Test
    fun savingWithoutAnyChangeIsRefusedAndTheMessageClearsOnTyping() = runTest(UnconfinedTestDispatcher()) {
        saveExpense()
        val vm = viewModel()

        vm.save()
        assertEquals(setOf(EditProblem.NOTHING_CHANGED), vm.uiState.value.problems)
        assertTrue(ledger.inner.corrections.isEmpty())

        vm.onAmountChange("8500")
        assertTrue(vm.uiState.value.problems.isEmpty())
    }

    @Test
    fun invalidInputShowsProblemsUnderTheFieldsAndSavesNothing() = runTest(UnconfinedTestDispatcher()) {
        saveExpense()
        val vm = viewModel()

        vm.onAmountChange("abc")
        vm.onDateSelect(today.plusDays(1))
        vm.onTitleChange("x".repeat(121))
        vm.onDescriptionChange("x".repeat(501))
        vm.save()

        assertEquals(
            setOf(EditProblem.AMOUNT_INVALID, EditProblem.DATE_IN_FUTURE, EditProblem.TITLE_TOO_LONG, EditProblem.DESCRIPTION_TOO_LONG),
            vm.uiState.value.problems,
        )
        assertTrue(ledger.inner.corrections.isEmpty())

        vm.onAmountChange("8500")
        assertFalse(EditProblem.AMOUNT_INVALID in vm.uiState.value.problems)
        assertTrue(EditProblem.DATE_IN_FUTURE in vm.uiState.value.problems)
    }

    @Test
    fun aValidSaveAddsOneCorrectionKeepsTheOriginalAndFinishes() = runTest(UnconfinedTestDispatcher()) {
        saveExpense()
        val vm = viewModel()
        vm.onAmountChange("8,900")
        vm.onCategorySelect("bills")
        vm.onSpendingClassSelect(SpendingClass.COMMITTED)
        vm.onDescriptionChange("")

        vm.save()

        assertTrue(vm.uiState.value.isFinished)
        assertEquals(1, ledger.inner.corrections.size)
        val saved = entry()
        assertEquals(lkr(8_400), saved.original.amount)
        assertEquals(lkr(8_900), saved.current.amount)
        assertEquals("bills", saved.current.categoryId)
        assertEquals(SpendingClass.COMMITTED, saved.current.spendingClass)
        assertNull(saved.current.note)
        assertEquals("Weekly groceries", saved.original.note)
    }

    @Test
    fun theCorrectionChangesTheTotalsOnce() = runTest(UnconfinedTestDispatcher()) {
        saveExpense()
        val vm = viewModel()
        vm.onAmountChange("9000")

        vm.save()
        vm.save()

        val period = YearMonth.from(today).toDateRange()
        assertEquals(lkr(9_000), LedgerSummaryCalculator.summarize(ledger.observeLedger().first(), period).spent.total.amount)
        assertEquals(1, ledger.inner.corrections.size)
    }

    @Test
    fun aRetryAfterALostAcknowledgementReusesTheSameCorrection() = runTest(UnconfinedTestDispatcher()) {
        saveExpense()
        val vm = viewModel()
        vm.onAmountChange("8900")
        ledger.correctionError = IOException("lost ack")
        ledger.correctionStoredBeforeError = true

        vm.save()
        assertFalse(vm.uiState.value.isFinished)
        assertEquals(EditMessage.FAILED, vm.uiState.value.message)

        ledger.correctionError = null
        vm.save()

        assertTrue(vm.uiState.value.isFinished)
        assertEquals(1, ledger.inner.corrections.size)
        assertEquals(2, ledger.correctionCalls)
    }

    @Test
    fun aFailedSaveKeepsWhatWasTypedAndShowsAMessage() = runTest(UnconfinedTestDispatcher()) {
        saveExpense()
        val vm = viewModel()
        vm.onAmountChange("8900")
        ledger.correctionError = IOException("boom")

        vm.save()

        assertEquals("8900", vm.uiState.value.input!!.amount)
        assertEquals(EditMessage.FAILED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(ledger.inner.corrections.isEmpty())
    }

    @Test
    fun anEntryVoidedWhileTheFormWasOpenCannotBeSaved() = runTest(UnconfinedTestDispatcher()) {
        saveExpense()
        val vm = viewModel()
        vm.onAmountChange("8900")
        ledger.inner.void(listOf(VoidMarker(TransactionId("e1"), com.example.zar.core.domain.TestLedger.T0)))

        vm.save()

        assertEquals(EditMessage.ALREADY_VOIDED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isFinished)
        assertTrue(ledger.inner.corrections.isEmpty())
    }

    @Test
    fun typingClearsTheMessageAndIgnoresInputBeforeTheEntryLoads() = runTest(UnconfinedTestDispatcher()) {
        saveExpense()
        val vm = viewModel()
        vm.onAmountChange("8900")
        ledger.correctionError = IOException("boom")
        vm.save()
        assertNotNull(vm.uiState.value.message)

        vm.onTitleChange("Keells")

        assertNull(vm.uiState.value.message)
    }

    @Test
    fun theOfflineFlagFollowsTheConnection() = runTest(UnconfinedTestDispatcher()) {
        saveExpense()
        val vm = viewModel()
        assertTrue(vm.uiState.value.isOnline)

        connectivity.online.value = false

        assertFalse(vm.uiState.value.isOnline)
    }
}
