package com.example.zar.features.transactions.presentation.refund

import androidx.lifecycle.SavedStateHandle
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.domain.refund.RefundProblem
import com.example.zar.features.transactions.domain.refund.usecase.ObserveRefundContextUseCase
import com.example.zar.features.transactions.domain.refund.usecase.RecordRefundUseCase
import com.example.zar.features.transactions.presentation.activity.FakeAccounts
import com.example.zar.features.transactions.presentation.activity.FakeConnectivity
import com.example.zar.features.transactions.presentation.refund.viewmodel.RefundFormViewModel
import com.example.zar.features.transactions.presentation.refund.viewmodel.RefundMessage
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The refund form's own failures: a save that fails, or fails after it was already stored (a lost acknowledgement). */
private class FlakySaveLedger(val inner: FakeLedgerRepository = FakeLedgerRepository()) : LedgerRepository by inner {
    var saveError: Throwable? = null
    var storedBeforeError = false
    var saveCalls = 0

    override suspend fun saveTransaction(transaction: Transaction) {
        saveCalls++
        val error = saveError
        if (error != null && !storedBeforeError) throw error
        inner.saveTransaction(transaction)
        if (error != null) throw error
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RefundFormViewModelTest {
    private val ledger = FlakySaveLedger()
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val connectivity = FakeConnectivity()
    private val today = ZarTime.today()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String = "p1") = RefundFormViewModel(
        savedStateHandle = SavedStateHandle(mapOf("purchaseId" to id)),
        observeContext = ObserveRefundContextUseCase(ledger, accounts),
        observeOnline = ObserveOnlineStatusUseCase(connectivity),
        recordRefund = RecordRefundUseCase(ledger, SaveTransactionUseCase(ledger)),
    )

    private suspend fun savePurchase(id: String = "p1", amount: Long = 8_400) {
        ledger.inner.saveTransaction(
            tx(id, TransactionType.EXPENSE, lkr(amount), cash, today).copy(merchant = "Keells Super", categoryId = "groceries"),
        )
    }

    private fun refunds() = ledger.inner.transactions.values.filter { it.type == TransactionType.REFUND }

    @Test
    fun theFormShowsThePurchaseAndHowMuchIsLeft() = runTest(UnconfinedTestDispatcher()) {
        savePurchase()
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.notRefundable)
        assertEquals(lkr(8_400), state.summary!!.remaining)
        assertEquals("LKR", state.currencyCode)
        assertEquals(cash, state.context!!.account)
        assertEquals("", state.amount)
    }

    @Test
    fun aMissingVoidedOrFullyRefundedPurchaseIsNotRefundable() = runTest(UnconfinedTestDispatcher()) {
        savePurchase("voided", 100)
        ledger.inner.void(listOf(VoidMarker(TransactionId("voided"), com.example.zar.core.domain.TestLedger.T0)))
        savePurchase("full", 100)
        ledger.inner.saveTransaction(tx("rf", TransactionType.REFUND, lkr(100), cash, today, refundOf = "full"))

        assertTrue(viewModel("missing").uiState.value.notRefundable)
        assertTrue(viewModel("voided").uiState.value.notRefundable)
        assertTrue(viewModel("full").uiState.value.notRefundable)
    }

    @Test
    fun invalidInputShowsProblemsAndSavesNothingThenClearsAsTheFieldIsFixed() = runTest(UnconfinedTestDispatcher()) {
        savePurchase()
        val vm = viewModel()

        vm.onAmountChange("abc")
        vm.onDateSelect(today.plusDays(1))
        vm.onNoteChange("x".repeat(501))
        vm.save()

        assertEquals(
            setOf(RefundProblem.AMOUNT_INVALID, RefundProblem.DATE_IN_FUTURE, RefundProblem.NOTE_TOO_LONG),
            vm.uiState.value.problems,
        )
        assertTrue(refunds().isEmpty())

        vm.onAmountChange("100")
        assertFalse(RefundProblem.AMOUNT_INVALID in vm.uiState.value.problems)
        assertTrue(RefundProblem.DATE_IN_FUTURE in vm.uiState.value.problems)
    }

    @Test
    fun anAmountOverWhatIsLeftIsRefusedWithTheCurrentLimit() = runTest(UnconfinedTestDispatcher()) {
        savePurchase()
        val vm = viewModel()
        // Another refund is saved while the form is open: the limit follows it without touching what was typed.
        vm.onAmountChange("7,000")
        ledger.inner.saveTransaction(tx("rf", TransactionType.REFUND, lkr(2_000), cash, today, refundOf = "p1"))

        assertEquals(lkr(6_400), vm.uiState.value.summary!!.remaining)
        assertEquals("7,000", vm.uiState.value.amount)
        vm.save()

        assertEquals(setOf(RefundProblem.AMOUNT_TOO_HIGH), vm.uiState.value.problems)
        assertEquals(1, refunds().size)
    }

    @Test
    fun aValidSaveAddsOneLinkedRefundOffsetsSpendingAndFinishes() = runTest(UnconfinedTestDispatcher()) {
        savePurchase()
        val vm = viewModel()
        vm.onAmountChange("2,000")
        vm.onNoteChange("  Damaged item ")

        vm.save()

        assertTrue(vm.uiState.value.isFinished)
        val refund = refunds().single()
        assertEquals(TransactionId("p1"), refund.refundOf)
        assertEquals(lkr(2_000), refund.amount)
        assertEquals("Damaged item", refund.note)
        assertEquals("Keells Super", refund.merchant)
        val spent = LedgerSummaryCalculator.summarize(ledger.observeLedger().first(), YearMonth.from(today).toDateRange())
        assertEquals(lkr(6_400), spent.spent.total.amount)
        assertEquals(lkr(0), spent.received.amount)
    }

    @Test
    fun theLastRefundDoesNotFlashTheNotRefundableMessage() = runTest(UnconfinedTestDispatcher()) {
        savePurchase(amount = 100)
        val vm = viewModel()
        vm.onAmountChange("100")

        vm.save()

        assertTrue(vm.uiState.value.isFinished)
        assertFalse(vm.uiState.value.notRefundable)
    }

    @Test
    fun retryingAfterALostAcknowledgementDoesNotRefundTwice() = runTest(UnconfinedTestDispatcher()) {
        savePurchase()
        val vm = viewModel()
        vm.onAmountChange("500")
        ledger.saveError = IOException("lost")
        ledger.storedBeforeError = true

        vm.save()
        assertEquals(RefundMessage.FAILED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isFinished)
        assertEquals(1, refunds().size)

        ledger.saveError = null
        vm.save()

        assertTrue(vm.uiState.value.isFinished)
        assertEquals(1, refunds().size)
        assertEquals(lkr(500), refunds().single().amount)
    }

    @Test
    fun aFailedSaveKeepsWhatWasTypedAndCanBeRetried() = runTest(UnconfinedTestDispatcher()) {
        savePurchase()
        val vm = viewModel()
        vm.onAmountChange("500")
        vm.onNoteChange("Wrong size")
        ledger.saveError = IOException("offline")

        vm.save()

        assertEquals(RefundMessage.FAILED, vm.uiState.value.message)
        assertEquals("500", vm.uiState.value.amount)
        assertEquals("Wrong size", vm.uiState.value.note)
        assertFalse(vm.uiState.value.isSaving)

        ledger.saveError = null
        vm.save()
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun theSavingErrorsAreMappedToTheirOwnMessages() = runTest(UnconfinedTestDispatcher()) {
        savePurchase()
        val vm = viewModel()
        vm.onAmountChange("500")

        ledger.saveError = LedgerError.NotSignedIn()
        vm.save()
        assertEquals(RefundMessage.NOT_SIGNED_IN, vm.uiState.value.message)

        ledger.saveError = LedgerError.RefundTooHigh()
        vm.save()
        assertEquals(RefundMessage.SAVED_FIRST, vm.uiState.value.message)
        assertEquals(setOf(RefundProblem.AMOUNT_TOO_HIGH), vm.uiState.value.problems)

        ledger.saveError = LedgerError.AlreadyVoided(TransactionId("p1"))
        vm.save()
        assertEquals(RefundMessage.ALREADY_VOIDED, vm.uiState.value.message)
        assertTrue(refunds().isEmpty())
    }

    @Test
    fun aPurchaseVoidedWhileTheFormIsOpenCannotTakeTheRefund() = runTest(UnconfinedTestDispatcher()) {
        savePurchase()
        val vm = viewModel()
        vm.onAmountChange("500")

        ledger.inner.void(listOf(VoidMarker(TransactionId("p1"), com.example.zar.core.domain.TestLedger.T0)))
        vm.save()

        assertTrue(vm.uiState.value.notRefundable)
        assertTrue(refunds().isEmpty())
    }

    @Test
    fun theFormSaysWhenThePhoneIsOffline() = runTest(UnconfinedTestDispatcher()) {
        savePurchase()
        val vm = viewModel()
        assertTrue(vm.uiState.value.isOnline)

        connectivity.online.value = false

        assertFalse(vm.uiState.value.isOnline)
        assertNull(vm.uiState.value.message)
    }
}
