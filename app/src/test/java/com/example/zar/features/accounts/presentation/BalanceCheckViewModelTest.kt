package com.example.zar.features.accounts.presentation

import androidx.lifecycle.SavedStateHandle
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.features.accounts.domain.reconcile.CheckAvailability
import com.example.zar.features.accounts.domain.reconcile.CheckOutcome
import com.example.zar.features.accounts.domain.reconcile.CheckProblem
import com.example.zar.features.accounts.domain.usecase.ObserveAccountDetailUseCase
import com.example.zar.features.accounts.domain.usecase.RecordBalanceCheckUseCase
import com.example.zar.features.accounts.presentation.viewmodel.BalanceCheckViewModel
import com.example.zar.features.accounts.presentation.viewmodel.CheckMessage
import com.example.zar.features.accounts.presentation.viewmodel.GapChoice
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.presentation.activity.FakeAccounts
import com.example.zar.features.transactions.presentation.activity.FakeConnectivity
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A ledger whose save can fail, or fail after it was already stored (a lost acknowledgement). */
private class FlakySaveLedger(val inner: FakeLedgerRepository = FakeLedgerRepository()) : LedgerRepository by inner {
    var pause: CompletableDeferred<Unit>? = null
    var saveError: Throwable? = null
    var storedBeforeError = false

    override suspend fun saveTransaction(transaction: Transaction) {
        pause?.await()
        val error = saveError
        if (error != null && !storedBeforeError) throw error
        inner.saveTransaction(transaction)
        if (error != null) throw error
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceCheckViewModelTest {
    private val ledger = FlakySaveLedger()
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val connectivity = FakeConnectivity()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String = "cash") = BalanceCheckViewModel(
        savedStateHandle = SavedStateHandle(mapOf("accountId" to id)),
        observeDetail = ObserveAccountDetailUseCase(accounts, ledger),
        observeOnline = ObserveOnlineStatusUseCase(connectivity),
        recordCheck = RecordBalanceCheckUseCase(ledger, SaveTransactionUseCase(ledger)),
    )

    private fun runSheet(id: String = "cash", block: suspend (BalanceCheckViewModel) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        // Cash opens at 2,000 and one 500 expense is recorded, so the recorded balance is 1,500.
        ledger.inner.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(500), cash, ZarTime.today()))
        val vm = viewModel(id)
        backgroundScope.launch { vm.uiState.collect { } }
        block(vm)
    }

    private fun adjustments() = ledger.inner.transactions.values.filter {
        it.type == TransactionType.ADJUSTMENT_OUT || it.type == TransactionType.ADJUSTMENT_IN
    }

    private suspend fun balanceNow() = AccountBalanceCalculator.calculate(cash, ledger.observeLedger().first()).current

    @Test
    fun theSheetShowsTheRecordedBalanceAndIsReadyToCheck() = runSheet { vm ->
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertEquals(lkr(1_500), state.balance!!.current)
        assertEquals(CheckAvailability.AVAILABLE, state.availability)
        assertNull(state.check)
        assertFalse(state.canFindMissing)
    }

    @Test
    fun typingTheCountShowsTheGapBeforeAnythingIsSaved() = runSheet { vm ->
        vm.onCountedChange("1,000")

        val check = vm.uiState.value.check!!
        assertEquals(CheckOutcome.SHORTFALL, check.outcome)
        assertEquals(lkr(500), check.gap)
        assertTrue(vm.uiState.value.canFindMissing)
        assertTrue(adjustments().isEmpty())
    }

    @Test
    fun aBlankOrUnreadableCountIsRefusedOnConfirmAndClearsWhenTyping() = runSheet { vm ->
        vm.confirm()
        assertEquals(setOf(CheckProblem.AMOUNT_MISSING), vm.uiState.value.problems)

        vm.onCountedChange("abc")
        assertTrue(vm.uiState.value.problems.isEmpty())
        vm.confirm()
        assertEquals(setOf(CheckProblem.AMOUNT_INVALID), vm.uiState.value.problems)
        assertTrue(adjustments().isEmpty())
    }

    @Test
    fun confirmingAShortfallSavesOneOutgoingAdjustmentAndFinishes() = runSheet { vm ->
        vm.onCountedChange("1000")

        vm.confirm()

        assertTrue(vm.uiState.value.isFinished)
        val adjustment = adjustments().single()
        assertEquals(TransactionType.ADJUSTMENT_OUT, adjustment.type)
        assertEquals(lkr(500), adjustment.amount)
        assertEquals(lkr(1_000), balanceNow())
    }

    @Test
    fun confirmingAnExtraSavesAnIncomingAdjustmentEvenIfFindWasPickedEarlier() = runSheet { vm ->
        vm.onCountedChange("1000")
        vm.onChoiceSelect(GapChoice.FIND)
        vm.onCountedChange("1800")
        assertFalse(vm.uiState.value.canFindMissing)

        vm.confirm()

        assertTrue(vm.uiState.value.isFinished)
        assertEquals(TransactionType.ADJUSTMENT_IN, adjustments().single().type)
        assertNull(vm.uiState.value.missingEntry)
    }

    @Test
    fun aMatchingCountFinishesWithoutSavingAnything() = runSheet { vm ->
        vm.onCountedChange("1500")

        vm.confirm()

        assertTrue(vm.uiState.value.isFinished)
        assertTrue(adjustments().isEmpty())
    }

    @Test
    fun findingTheMissingEntryHandsOverTheGapAndSavesNoAdjustment() = runSheet { vm ->
        vm.onCountedChange("1000")
        vm.onChoiceSelect(GapChoice.FIND)

        vm.confirm()

        val request = vm.uiState.value.missingEntry!!
        assertEquals(AccountId("cash"), request.accountId)
        assertEquals("500", request.amount)
        assertTrue(adjustments().isEmpty())
        assertFalse(vm.uiState.value.isFinished)

        vm.onMissingEntryHandled()
        assertNull(vm.uiState.value.missingEntry)
    }

    @Test
    fun retryingAfterALostAcknowledgementDoesNotAdjustTwice() = runSheet { vm ->
        vm.onCountedChange("1000")
        ledger.saveError = IOException("lost")
        ledger.storedBeforeError = true

        vm.confirm()
        assertEquals(CheckMessage.FAILED, vm.uiState.value.message)
        assertEquals(1, adjustments().size)

        ledger.saveError = null
        vm.confirm()

        assertTrue(vm.uiState.value.isFinished)
        assertEquals(1, adjustments().size)
        assertEquals(lkr(1_000), balanceNow())
    }

    @Test
    fun aFailedSaveKeepsTheCountAndCanBeRetried() = runSheet { vm ->
        vm.onCountedChange("1000")
        ledger.saveError = IOException("offline")

        vm.confirm()

        assertEquals(CheckMessage.FAILED, vm.uiState.value.message)
        assertEquals("1000", vm.uiState.value.counted)
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(adjustments().isEmpty())

        ledger.saveError = null
        vm.confirm()
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun signedOutIsReportedAsSuch() = runSheet { vm ->
        vm.onCountedChange("1000")
        ledger.saveError = LedgerError.NotSignedIn()

        vm.confirm()

        assertEquals(CheckMessage.NOT_SIGNED_IN, vm.uiState.value.message)
    }

    @Test
    fun anAccountWithNoOpeningValueCannotBeChecked() = runTest(UnconfinedTestDispatcher()) {
        accounts.accounts.value = listOf(cash.copy(openingBalance = OpeningBalance.Unknown))
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        vm.onCountedChange("100")

        vm.confirm()

        assertEquals(CheckAvailability.BALANCE_UNKNOWN, vm.uiState.value.availability)
        assertNull(vm.uiState.value.check)
        assertTrue(adjustments().isEmpty())
        assertFalse(vm.uiState.value.isFinished)
    }

    @Test
    fun anUnknownAccountHasNothingToCheck() = runSheet("missing") { vm ->
        assertNull(vm.uiState.value.balance)
        assertNull(vm.uiState.value.availability)
    }

    @Test
    fun resetStartsTheNextCheckFromScratch() = runSheet { vm ->
        vm.onCountedChange("1000")
        vm.onChoiceSelect(GapChoice.FIND)
        vm.confirm()
        assertNotNull(vm.uiState.value.missingEntry)

        vm.reset()

        val state = vm.uiState.value
        assertEquals("", state.counted)
        assertEquals(GapChoice.LEAVE, state.choice)
        assertNull(state.missingEntry)
        assertFalse(state.isFinished)
    }

    @Test
    fun theSheetSaysWhenThePhoneIsOffline() = runSheet { vm ->
        assertTrue(vm.uiState.value.isOnline)

        connectivity.online.value = false

        assertFalse(vm.uiState.value.isOnline)
    }

    @Test fun resetCannotStartANewCheckWhileTheCurrentSaveIsPending() = runSheet { vm ->
        val gate = CompletableDeferred<Unit>()
        ledger.pause = gate
        vm.onCountedChange("1000")
        vm.confirm()
        assertTrue(vm.uiState.value.isSaving)
        vm.reset()
        assertTrue(vm.uiState.value.isSaving)
        vm.confirm()
        gate.complete(Unit)
        assertTrue(vm.uiState.value.isFinished)
        assertEquals(1, adjustments().size)
    }
}
