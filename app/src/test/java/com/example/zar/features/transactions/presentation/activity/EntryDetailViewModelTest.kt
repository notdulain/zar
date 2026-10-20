package com.example.zar.features.transactions.presentation.activity

import androidx.lifecycle.SavedStateHandle
import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.features.transactions.domain.activity.usecase.ObserveEntryDetailUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.presentation.activity.viewmodel.EntryDetailContent
import com.example.zar.features.transactions.presentation.activity.viewmodel.EntryDetailViewModel
import com.example.zar.features.transactions.presentation.activity.viewmodel.VoidProblem
import java.io.IOException
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
class EntryDetailViewModelTest {
    private val ledger = FlakyLedger()
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val connectivity = FakeConnectivity()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String) = EntryDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("entryId" to id)),
        observeDetail = ObserveEntryDetailUseCase(ledger, accounts),
        observeOnline = ObserveOnlineStatusUseCase(connectivity),
        voidTransaction = VoidTransactionUseCase(ledger),
    )

    private fun runScreen(id: String, block: suspend (EntryDetailViewModel) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(id)
        backgroundScope.launch { vm.uiState.collect { } }
        block(vm)
    }

    private fun EntryDetailViewModel.detail() = (uiState.value.content as EntryDetailContent.Ready).detail

    private suspend fun saveExpense(id: String = "e1") {
        ledger.save(tx(id, TransactionType.EXPENSE, lkr(8_400), cash).copy(merchant = "Keells Super", categoryId = "groceries"))
    }

    private suspend fun saveTransferWithFee() {
        val fee = Transaction(
            id = TransactionId("t1-fee"), accountId = bank.id, type = TransactionType.EXPENSE, amount = lkr(30), date = OCT_10,
            reportingConversion = CurrencyConverter.toReporting(lkr(30), null), spendingClass = SpendingClass.COMMITTED,
            categoryId = "transfer-fee", merchant = "Transfer fee", createdAt = T0,
        )
        ledger.inner.saveTransfer(
            Transfer(
                id = TransferId("t1"), fromAccountId = bank.id, toAccountId = cash.id, sent = lkr(5_000), received = lkr(5_000), date = OCT_10,
                sentConversion = CurrencyConverter.toReporting(lkr(5_000), null),
                receivedConversion = CurrencyConverter.toReporting(lkr(5_000), null), createdAt = T0, fee = fee,
            ),
        )
    }

    @Test
    fun showsTheEntryItWasOpenedFor() = runScreen("e1") { vm ->
        saveExpense()

        assertEquals("e1", vm.detail().entry.id.value)
        assertEquals(cash, vm.detail().account)
        assertFalse(vm.uiState.value.showVoidSheet)
    }

    @Test
    fun anUnknownEntryIsNotFound() = runScreen("missing") { vm ->
        assertEquals(EntryDetailContent.NotFound, vm.uiState.value.content)
    }

    @Test
    fun aFailedLoadShowsAnError() = runTest(UnconfinedTestDispatcher()) {
        ledger.loadFailures = 1
        val vm = viewModel("e1")
        backgroundScope.launch { vm.uiState.collect { } }

        assertEquals(EntryDetailContent.Error, vm.uiState.value.content)
    }

    @Test
    fun theVoidSheetOpensAndClosesWithoutChangingAnything() = runScreen("e1") { vm ->
        saveExpense()

        vm.showVoidSheet()
        assertTrue(vm.uiState.value.showVoidSheet)
        vm.dismissVoidSheet()

        assertFalse(vm.uiState.value.showVoidSheet)
        assertTrue(ledger.inner.voids.isEmpty())
    }

    @Test
    fun confirmingVoidsTheEntryClosesTheSheetAndMarksItVoided() = runScreen("e1") { vm ->
        saveExpense()
        vm.showVoidSheet()

        vm.confirmVoid()

        assertEquals(setOf(TransactionId("e1")), ledger.inner.voids.keys)
        assertFalse(vm.uiState.value.showVoidSheet)
        assertFalse(vm.uiState.value.isVoiding)
        assertTrue(vm.detail().entry.isVoided)
        assertFalse(vm.detail().canVoid)
    }

    @Test
    fun voidingATransferVoidsBothLegsAndTheFeeInOneGo() = runScreen("t1-out") { vm ->
        saveTransferWithFee()
        assertEquals("t1-fee", vm.detail().fee!!.id.value)
        vm.showVoidSheet()

        vm.confirmVoid()

        assertEquals(setOf("t1-out", "t1-in", "t1-fee"), ledger.inner.voids.keys.map { it.value }.toSet())
        assertTrue(vm.detail().entry.isVoided)
    }

    @Test
    fun aFailedVoidKeepsTheSheetOpenWithAMessageAndCanBeRetried() = runScreen("e1") { vm ->
        saveExpense()
        ledger.voidError = IOException("no network")
        vm.showVoidSheet()

        vm.confirmVoid()

        assertTrue(vm.uiState.value.showVoidSheet)
        assertEquals(VoidProblem.FAILED, vm.uiState.value.voidProblem)
        assertFalse(vm.uiState.value.isVoiding)
        assertTrue(ledger.inner.voids.isEmpty())

        ledger.voidError = null
        vm.confirmVoid()

        assertFalse(vm.uiState.value.showVoidSheet)
        assertEquals(null, vm.uiState.value.voidProblem)
        assertEquals(1, ledger.inner.voids.size)
    }

    @Test
    fun signedOutIsReportedSeparately() = runScreen("e1") { vm ->
        saveExpense()
        ledger.voidError = LedgerError.NotSignedIn()

        vm.confirmVoid()

        assertEquals(VoidProblem.NOT_SIGNED_IN, vm.uiState.value.voidProblem)
    }

    @Test
    fun confirmingTwiceVoidsOnlyOnce() = runScreen("e1") { vm ->
        saveExpense()
        vm.showVoidSheet()

        vm.confirmVoid()
        vm.confirmVoid()

        assertEquals(1, ledger.inner.voids.size)
    }

    @Test
    fun aVoidedEntryCannotBeVoidedAgainFromTheScreen() = runScreen("e1") { vm ->
        saveExpense()
        vm.confirmVoid()
        val first = ledger.inner.voids.getValue(TransactionId("e1"))

        vm.confirmVoid()

        assertEquals(first, ledger.inner.voids.getValue(TransactionId("e1")))
        assertEquals(1, ledger.inner.voids.size)
    }

    @Test
    fun theOfflineFlagFollowsTheConnectionAndTheVoidStillWorks() = runScreen("e1") { vm ->
        saveExpense()
        connectivity.online.value = false
        assertFalse(vm.uiState.value.isOnline)

        vm.confirmVoid()

        assertEquals(1, ledger.inner.voids.size)
    }
}
