package com.example.zar.features.catchup.presentation

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.features.accounts.domain.reconcile.CheckAvailability
import com.example.zar.features.accounts.domain.reconcile.CheckOutcome
import com.example.zar.features.accounts.domain.reconcile.CheckProblem
import com.example.zar.features.accounts.domain.usecase.RecordBalanceCheckUseCase
import com.example.zar.features.catchup.FakeCatchUpPreferences
import com.example.zar.features.catchup.FakeReminderScheduler
import com.example.zar.features.catchup.domain.usecase.ObserveCatchUpPreferencesUseCase
import com.example.zar.features.catchup.domain.usecase.ObserveCatchUpUseCase
import com.example.zar.features.catchup.domain.usecase.RecordCatchUpUseCase
import com.example.zar.features.catchup.domain.usecase.UpdateReminderSettingsUseCase
import com.example.zar.features.catchup.presentation.viewmodel.CatchUpMessage
import com.example.zar.features.catchup.presentation.viewmodel.CatchUpStep
import com.example.zar.features.catchup.presentation.viewmodel.CatchUpViewModel
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.presentation.activity.FakeAccounts
import com.example.zar.features.transactions.presentation.activity.FakeConnectivity
import java.io.IOException
import java.time.DayOfWeek
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
private class FlakyCatchUpLedger(val inner: FakeLedgerRepository = FakeLedgerRepository()) : LedgerRepository by inner {
    var saveError: Throwable? = null
    var storedBeforeError = false

    override suspend fun saveTransaction(transaction: Transaction) {
        val error = saveError
        if (error != null && !storedBeforeError) throw error
        inner.saveTransaction(transaction)
        if (error != null) throw error
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CatchUpViewModelTest {
    private val ledger = FlakyCatchUpLedger()
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val prefs = FakeCatchUpPreferences()
    private val scheduler = FakeReminderScheduler()
    private val connectivity = FakeConnectivity()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = CatchUpViewModel(
        observeCatchUp = ObserveCatchUpUseCase(ledger, accounts, prefs),
        observePreferences = ObserveCatchUpPreferencesUseCase(prefs),
        observeOnline = ObserveOnlineStatusUseCase(connectivity),
        recordCatchUp = RecordCatchUpUseCase(RecordBalanceCheckUseCase(ledger, SaveTransactionUseCase(ledger)), prefs),
        updateReminder = UpdateReminderSettingsUseCase(prefs, scheduler),
    )

    private fun runFlow(block: suspend kotlinx.coroutines.test.TestScope.(CatchUpViewModel) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        // Cash opens at 2,000 and one 500 expense is recorded, so the recorded balance is 1,500.
        ledger.inner.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(500), cash, ZarTime.today(), SpendingClass.UNKNOWN))
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        block(vm)
    }

    private fun adjustments() = ledger.inner.transactions.values.filter {
        it.type == TransactionType.ADJUSTMENT_OUT || it.type == TransactionType.ADJUSTMENT_IN
    }

    private suspend fun balanceNow() = AccountBalanceCalculator.calculate(cash, ledger.observeLedger().first()).current

    private fun CatchUpViewModel.toSaveStep(counted: String) {
        startCounting()
        onCountedChange(counted)
        reviewAdjustment()
    }

    @Test
    fun itStartsOnTheReviewStepWithTheOpenItemsAndTheCashAccount() = runFlow { vm ->
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertEquals(CatchUpStep.REVIEW, state.step)
        assertEquals(listOf("e1"), state.items.map { it.entryId.value })
        assertEquals(lkr(1_500), state.balance!!.current)
        assertEquals(CheckAvailability.AVAILABLE, state.availability)
        assertNull(state.result)
    }

    @Test
    fun continuingMovesToCountingWithoutSavingAnything() = runFlow { vm ->
        vm.startCounting()

        assertEquals(CatchUpStep.COUNT, vm.uiState.value.step)
        assertTrue(adjustments().isEmpty())
    }

    @Test
    fun aBlankOrUnreadableCountIsRefusedAndStaysOnTheCountStep() = runFlow { vm ->
        vm.startCounting()
        vm.reviewAdjustment()
        assertEquals(setOf(CheckProblem.AMOUNT_MISSING), vm.uiState.value.problems)
        assertEquals(CatchUpStep.COUNT, vm.uiState.value.step)

        vm.onCountedChange("abc")
        assertTrue(vm.uiState.value.problems.isEmpty())
        vm.reviewAdjustment()
        assertEquals(setOf(CheckProblem.AMOUNT_INVALID), vm.uiState.value.problems)
        assertEquals(CatchUpStep.COUNT, vm.uiState.value.step)
    }

    @Test
    fun typingTheCountShowsTheGapBeforeAnythingIsSaved() = runFlow { vm ->
        vm.startCounting()
        vm.onCountedChange("1,000")

        val check = vm.uiState.value.check!!
        assertEquals(CheckOutcome.SHORTFALL, check.outcome)
        assertEquals(lkr(500), check.gap)
        assertTrue(adjustments().isEmpty())
    }

    @Test
    fun aValidCountMovesToTheSaveStepAndBackStepsThroughTheFlow() = runFlow { vm ->
        vm.toSaveStep("1000")
        assertEquals(CatchUpStep.SAVE, vm.uiState.value.step)

        assertTrue(vm.back())
        assertEquals(CatchUpStep.COUNT, vm.uiState.value.step)
        assertEquals("1000", vm.uiState.value.counted)
        assertTrue(vm.back())
        assertEquals(CatchUpStep.REVIEW, vm.uiState.value.step)
        // Nothing earlier: the screen closes instead.
        assertFalse(vm.back())
        assertTrue(adjustments().isEmpty())
    }

    @Test
    fun savingAShortfallSavesOneCashCatchUpAdjustmentAndFinishes() = runFlow { vm ->
        vm.toSaveStep("1000")

        vm.save()

        val state = vm.uiState.value
        assertEquals(CatchUpStep.DONE, state.step)
        assertEquals(CheckOutcome.SHORTFALL, state.result!!.outcome)
        val adjustment = adjustments().single()
        assertEquals(TransactionType.ADJUSTMENT_OUT, adjustment.type)
        assertEquals(lkr(500), adjustment.amount)
        assertEquals(RecordSource.CASH_CATCH_UP, adjustment.source)
        assertNull(adjustment.categoryId)
        assertEquals(lkr(1_000), balanceNow())
        assertEquals(ZarTime.today(), prefs.current.lastCatchUp)
        assertNotNull(state.nextReminder)
        // The new gap is itself an open item, so catch-up never claims everything is resolved.
        assertTrue(state.items.any { it.entryId == adjustment.id })
    }

    @Test
    fun aMatchingCountSavesNothingButFinishes() = runFlow { vm ->
        vm.toSaveStep("1500")

        vm.save()

        assertEquals(CatchUpStep.DONE, vm.uiState.value.step)
        assertEquals(CheckOutcome.MATCHES, vm.uiState.value.result!!.outcome)
        assertTrue(adjustments().isEmpty())
        assertEquals(ZarTime.today(), prefs.current.lastCatchUp)
    }

    @Test
    fun anExtraSavesAnIncomingAdjustment() = runFlow { vm ->
        vm.toSaveStep("1800")

        vm.save()

        assertEquals(TransactionType.ADJUSTMENT_IN, adjustments().single().type)
        assertEquals(lkr(1_800), balanceNow())
    }

    @Test
    fun aFailedSaveStaysOnTheSaveStepAndARetryDoesNotAdjustTwice() = runFlow { vm ->
        vm.toSaveStep("1000")
        // The save is stored but the acknowledgement is lost, so the screen sees a failure.
        ledger.saveError = IOException("lost")
        ledger.storedBeforeError = true

        vm.save()
        assertEquals(CatchUpStep.SAVE, vm.uiState.value.step)
        assertEquals(CatchUpMessage.FAILED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isSaving)

        ledger.saveError = null
        vm.save()

        assertEquals(CatchUpStep.DONE, vm.uiState.value.step)
        assertEquals(1, adjustments().size)
        assertEquals(lkr(1_000), balanceNow())
        // The result is what the person confirmed: the retry found the gap already closed, but one was saved.
        assertEquals(CheckOutcome.SHORTFALL, vm.uiState.value.result!!.outcome)
    }

    @Test
    fun aLostAcknowledgementKeepsTheConfirmedPreviewAndBackClosesInsteadOfReusingItsIdForAnotherCount() = runFlow { vm ->
        vm.toSaveStep("1000")
        ledger.saveError = IOException("lost")
        ledger.storedBeforeError = true
        vm.save()

        assertEquals(CheckOutcome.SHORTFALL, vm.uiState.value.check!!.outcome)
        assertEquals(lkr(500), vm.uiState.value.check!!.gap)
        assertTrue(vm.uiState.value.hasSaveAttempt)
        assertFalse(vm.back())
        vm.onCountedChange("700")
        assertEquals("1000", vm.uiState.value.counted)
        ledger.saveError = null
        vm.save()
        assertEquals(1, adjustments().size)
        assertEquals(adjustments().single().id, vm.uiState.value.adjustmentId)
    }

    @Test
    fun aFailedSkipKeepsTheCatchUpOpenAndShowsAnError() = runFlow { vm ->
        prefs.saveError = IOException("disk full")
        vm.skipThisWeek()
        assertFalse(vm.uiState.value.isLeaving)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(CatchUpMessage.SKIP_FAILED, vm.uiState.value.message)
        prefs.saveError = null
        vm.skipThisWeek()
        assertTrue(vm.uiState.value.isLeaving)
    }

    @Test
    fun savingWhileSignedOutSaysToSignIn() = runFlow { vm ->
        vm.toSaveStep("1000")
        ledger.saveError = LedgerError.NotSignedIn()

        vm.save()

        assertEquals(CatchUpMessage.NOT_SIGNED_IN, vm.uiState.value.message)
        assertEquals(CatchUpStep.SAVE, vm.uiState.value.step)
        assertTrue(adjustments().isEmpty())
    }

    @Test
    fun savingTwiceInARowOnlySavesOnce() = runFlow { vm ->
        vm.toSaveStep("1000")

        vm.save()
        vm.save()

        assertEquals(1, adjustments().size)
    }

    @Test
    fun theOfflineFlagFollowsTheConnection() = runFlow { vm ->
        assertTrue(vm.uiState.value.isOnline)

        connectivity.online.value = false

        assertFalse(vm.uiState.value.isOnline)
    }

    @Test
    fun withoutACashAccountThereIsNothingToCount() = runTest(UnconfinedTestDispatcher()) {
        val noCash = FakeAccounts(listOf(bank))
        val vm = CatchUpViewModel(
            ObserveCatchUpUseCase(ledger, noCash, prefs),
            ObserveCatchUpPreferencesUseCase(prefs),
            ObserveOnlineStatusUseCase(connectivity),
            RecordCatchUpUseCase(RecordBalanceCheckUseCase(ledger, SaveTransactionUseCase(ledger)), prefs),
            UpdateReminderSettingsUseCase(prefs, scheduler),
        )
        backgroundScope.launch { vm.uiState.collect { } }

        vm.startCounting()
        vm.reviewAdjustment()

        assertNull(vm.uiState.value.balance)
        assertTrue(vm.uiState.value.cashAccounts.isEmpty())
        assertEquals(CatchUpStep.COUNT, vm.uiState.value.step)
    }

    @Test
    fun anAccountWithNoOpeningValueCannotBeCounted() = runTest(UnconfinedTestDispatcher()) {
        val unknown = cash.copy(openingBalance = OpeningBalance.Unknown)
        val vm = CatchUpViewModel(
            ObserveCatchUpUseCase(ledger, FakeAccounts(listOf(unknown)), prefs),
            ObserveCatchUpPreferencesUseCase(prefs),
            ObserveOnlineStatusUseCase(connectivity),
            RecordCatchUpUseCase(RecordBalanceCheckUseCase(ledger, SaveTransactionUseCase(ledger)), prefs),
            UpdateReminderSettingsUseCase(prefs, scheduler),
        )
        backgroundScope.launch { vm.uiState.collect { } }

        vm.startCounting()
        vm.onCountedChange("1000")
        vm.reviewAdjustment()

        assertEquals(CheckAvailability.BALANCE_UNKNOWN, vm.uiState.value.availability)
        assertEquals(CatchUpStep.COUNT, vm.uiState.value.step)
        assertTrue(adjustments().isEmpty())
    }

    @Test
    fun withTwoCashAccountsThePersonChoosesWhichToCount() = runTest(UnconfinedTestDispatcher()) {
        val wallet: Account = cash.copy(id = AccountId("wallet"), name = "Wallet", openingBalance = OpeningBalance.Known(lkr(300)))
        val vm = CatchUpViewModel(
            ObserveCatchUpUseCase(ledger, FakeAccounts(listOf(cash, wallet)), prefs),
            ObserveCatchUpPreferencesUseCase(prefs),
            ObserveOnlineStatusUseCase(connectivity),
            RecordCatchUpUseCase(RecordBalanceCheckUseCase(ledger, SaveTransactionUseCase(ledger)), prefs),
            UpdateReminderSettingsUseCase(prefs, scheduler),
        )
        backgroundScope.launch { vm.uiState.collect { } }
        // Accounts are listed by name, so Cash comes first and is counted by default.
        assertEquals("Cash", vm.uiState.value.balance!!.account.name)
        vm.startCounting()
        vm.onCountedChange("250")

        vm.onAccountSelect(wallet.id)

        assertEquals("Wallet", vm.uiState.value.balance!!.account.name)
        assertEquals(lkr(300), vm.uiState.value.balance!!.current)
        assertEquals("", vm.uiState.value.counted)
    }

    @Test
    fun skippingTheWeekSkipsOneReminderLeavesAndChangesNoRecords() = runFlow { vm ->
        vm.skipThisWeek()

        val reminder = prefs.current.reminder
        val skipped = reminder.skippedOccurrence
        assertNotNull(skipped)
        assertEquals(DayOfWeek.SUNDAY, skipped!!.dayOfWeek)
        assertTrue(reminder.enabled)
        assertTrue(vm.uiState.value.isLeaving)
        assertTrue(adjustments().isEmpty())
        assertNull(prefs.current.lastCatchUp)
        // The reminder that was arranged is the one after the skipped Sunday.
        assertTrue(scheduler.last!!.toLocalDate().isAfter(skipped))
    }
}
