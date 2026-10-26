package com.example.zar.features.quicknote.presentation

import com.example.zar.features.transactions.domain.category.FakeExpenseCategoryRepository
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.ObserveLedgerUseCase
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.features.accounts.domain.usecase.ObserveActiveAccountsUseCase
import com.example.zar.features.freelance.FakeExpectedPaymentRepository
import com.example.zar.features.quicknote.QuickNoteTestSupport.Session
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftRequest
import com.example.zar.features.quicknote.domain.repository.QuickNoteDraftRepository
import com.example.zar.features.quicknote.domain.review.ProposalBlocker
import com.example.zar.features.quicknote.domain.review.ProposalInput
import com.example.zar.features.quicknote.domain.usecase.ConfirmQuickNoteProposalUseCase
import com.example.zar.features.quicknote.domain.usecase.GenerateQuickNoteDraftUseCase
import com.example.zar.features.quicknote.presentation.viewmodel.ProposalStatus
import com.example.zar.features.quicknote.presentation.viewmodel.QuickNoteFailure
import com.example.zar.features.quicknote.presentation.viewmodel.QuickNoteStage
import com.example.zar.features.quicknote.presentation.viewmodel.QuickNoteViewModel
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.presentation.activity.FakeAccounts
import com.example.zar.features.transactions.presentation.activity.FakeConnectivity
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class QuickNoteViewModelTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val connectivity = FakeConnectivity()
    private val ledger = FakeLedgerRepository()
    private val expected = FakeExpectedPaymentRepository()

    /** Counts every call: each real one costs money, so the tests pin down exactly when one is made. */
    private class CountingDrafts(var answer: suspend () -> Draft) : QuickNoteDraftRepository {
        var calls = 0
        override suspend fun generate(request: QuickNoteDraftRequest): Draft {
            calls++
            return answer()
        }
    }

    private fun draftOf(vararg proposals: DraftProposal) =
        Draft(DraftId("note1"), DraftSource.QUICK_NOTE, proposals.toList(), DraftStatus.PENDING_REVIEW, T0)

    private val groceries = DraftProposal.ActualTransaction(
        type = TransactionType.EXPENSE, amount = lkr(1_250), accountId = cash.id, date = today.minusDays(1),
        spendingClass = SpendingClass.DISCRETIONARY, categoryId = "groceries", merchant = "Keells Super",
        uncertainFields = setOf(DraftField.CATEGORY),
    )
    private val noAccount = groceries.copy(accountId = null)
    private val bill = DraftProposal.Expected(Direction.OUT, lkr(4_500), today.plusDays(5), "Electricity bill")

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun run(drafts: CountingDrafts, saves: LedgerRepository = ledger, block: suspend (QuickNoteViewModel, CountingDrafts) -> Unit) =
        runTest(UnconfinedTestDispatcher()) { block(viewModel(drafts, saves), drafts) }

    private fun viewModel(drafts: CountingDrafts, saves: LedgerRepository = ledger) = QuickNoteViewModel(
        generate = GenerateQuickNoteDraftUseCase(drafts, accounts, Session(), { today }),
        confirmProposal = ConfirmQuickNoteProposalUseCase(accounts, saves, expected, SaveTransactionUseCase(saves), SaveTransferUseCase(saves), FakeExpenseCategoryRepository()),
        observeAccounts = ObserveActiveAccountsUseCase(accounts),
        observeLedger = ObserveLedgerUseCase(ledger),
        observeOnline = ObserveOnlineStatusUseCase(connectivity),
        today = { today },
        now = { T0 },
    )

    // --- Reading is explicit and rare ----------------------------------------------------------

    @Test
    fun typingNeverReadsTheNote() = run(CountingDrafts { draftOf(groceries) }) { vm, drafts ->
        vm.onNoteChange("paid 1250 at keells")
        vm.onNoteChange("paid 1250 at keells super with cash")

        assertEquals(0, drafts.calls)
        assertEquals(QuickNoteStage.WRITING, vm.uiState.value.stage)
    }

    @Test
    fun aBlankNoteIsNeverSent() = run(CountingDrafts { draftOf(groceries) }) { vm, drafts ->
        vm.onNoteChange("   ")
        vm.interpret()
        assertEquals(0, drafts.calls)
    }

    @Test
    fun offlineNothingIsSentAndTheNoteIsKept() = run(CountingDrafts { draftOf(groceries) }) { vm, drafts ->
        connectivity.online.value = false
        vm.onNoteChange("paid 1250 at keells")
        vm.interpret()

        assertEquals(0, drafts.calls)
        assertFalse(vm.uiState.value.canInterpret)
        assertEquals("paid 1250 at keells", vm.uiState.value.note)
    }

    @Test
    fun aSecondTapWhileReadingDoesNotSendTheNoteAgain() {
        val gate = CompletableDeferred<Draft>()
        run(CountingDrafts { gate.await() }) { vm, drafts ->
            vm.onNoteChange("paid 1250 at keells")
            vm.interpret()
            vm.interpret()
            vm.interpret()

            assertEquals(1, drafts.calls)
            assertEquals(QuickNoteStage.READING, vm.uiState.value.stage)
            gate.complete(draftOf(groceries))
            assertEquals(1, drafts.calls)
        }
    }

    @Test
    fun theNoteIsCutToTheServiceLimit() = run(CountingDrafts { draftOf(groceries) }) { vm, _ ->
        vm.onNoteChange("x".repeat(900))
        assertEquals(500, vm.uiState.value.note.length)
    }

    // --- Review --------------------------------------------------------------------------------

    @Test
    fun aReadNoteOpensTheReviewWithWhatIsMissingMarked() = run(CountingDrafts { draftOf(noAccount, bill) }) { vm, drafts ->
        vm.onNoteChange("note")
        vm.interpret()

        val state = vm.uiState.value
        assertEquals(QuickNoteStage.REVIEWING, state.stage)
        assertEquals(2, state.proposals.size)
        assertEquals(setOf(ProposalBlocker.ACCOUNT_MISSING), state.proposals[0].blockers)
        assertTrue(state.proposals[1].blockers.isEmpty())
        assertEquals(1, drafts.calls)
        assertTrue("Nothing is saved by reading", ledger.transactions.isEmpty() && expected.payments.value.isEmpty())
    }

    @Test
    fun aProposalWithAGapCannotBeSavedUntilTheGapIsFilled() = run(CountingDrafts { draftOf(noAccount) }) { vm, _ ->
        vm.onNoteChange("note")
        vm.interpret()

        vm.confirm(0)
        assertTrue(ledger.transactions.isEmpty())

        vm.update(0) { (it as ProposalInput.Actual).copy(accountId = cash.id) }
        assertTrue(vm.uiState.value.proposals[0].canSave)
        vm.confirm(0)
        assertEquals(1, ledger.transactions.size)
    }

    @Test
    fun touchingAGuessedFieldStopsItBeingMarkedAsGuessed() = run(CountingDrafts { draftOf(groceries) }) { vm, _ ->
        vm.onNoteChange("note")
        vm.interpret()
        assertEquals(setOf(DraftField.CATEGORY), vm.uiState.value.proposals[0].input.uncertain)

        vm.update(0) { (it as ProposalInput.Actual).copy(categoryId = "bills") }

        assertTrue(vm.uiState.value.proposals[0].input.uncertain.isEmpty())
    }

    @Test
    fun savingTwiceInARowSavesOnce() = run(CountingDrafts { draftOf(groceries, bill) }) { vm, _ ->
        vm.onNoteChange("note")
        vm.interpret()

        vm.confirm(0)
        vm.confirm(0)

        assertEquals(1, ledger.transactions.size)
        assertEquals(ProposalStatus.SAVED, vm.uiState.value.proposals[0].status)
        assertEquals(ProposalStatus.OPEN, vm.uiState.value.proposals[1].status)
        assertFalse(vm.uiState.value.isFinished)
    }

    @Test
    fun confirmingEveryProposalFinishesAndDropsTheNote() = run(CountingDrafts { draftOf(groceries, bill) }) { vm, _ ->
        vm.onNoteChange("note")
        vm.interpret()
        vm.confirm(0)
        vm.confirm(1)

        val state = vm.uiState.value
        assertTrue(state.isFinished)
        assertEquals("", state.note)
        assertEquals(1, ledger.transactions.size)
        assertEquals(1, expected.payments.value.size)
    }

    @Test
    fun discardingSomeKeepsTheRestToReview() = run(CountingDrafts { draftOf(groceries, bill) }) { vm, _ ->
        vm.onNoteChange("note")
        vm.interpret()
        vm.discard(1)

        assertEquals(ProposalStatus.DISCARDED, vm.uiState.value.proposals[1].status)
        assertFalse(vm.uiState.value.isFinished)
        assertTrue(ledger.transactions.isEmpty() && expected.payments.value.isEmpty())
    }

    @Test
    fun discardAllKeepsWhatWasAlreadySavedAndFinishes() = run(CountingDrafts { draftOf(groceries, bill) }) { vm, _ ->
        vm.onNoteChange("note")
        vm.interpret()
        vm.confirm(0)
        vm.discardAll()

        assertTrue(vm.uiState.value.isFinished)
        assertEquals(1, ledger.transactions.size)
        assertTrue(expected.payments.value.isEmpty())
    }

    @Test
    fun aFailedSaveKeepsTheProposalAndARetrySavesOneRecord() {
        class FailsOnce(val inner: FakeLedgerRepository) : LedgerRepository by inner {
            var fail = true
            override suspend fun saveTransaction(transaction: Transaction) {
                if (fail) {
                    fail = false
                    throw IOException("offline")
                }
                inner.saveTransaction(transaction)
            }
        }
        val flaky = FailsOnce(ledger)
        run(CountingDrafts { draftOf(groceries) }, flaky) { vm, _ ->
            vm.onNoteChange("note")
            vm.interpret()

            vm.confirm(0)
            assertTrue(vm.uiState.value.proposals[0].saveFailed)
            assertEquals(ProposalStatus.OPEN, vm.uiState.value.proposals[0].status)

            vm.confirm(0)
            assertEquals(1, ledger.transactions.size)
            assertTrue(vm.uiState.value.isFinished)
        }
    }

    @Test
    fun theNoteCanBeRewordedOnlyBeforeAnythingIsSaved() = run(CountingDrafts { draftOf(groceries, bill) }) { vm, _ ->
        vm.onNoteChange("note")
        vm.interpret()
        vm.confirm(0)
        vm.editNote()
        assertEquals("Saved records are never read a second time", QuickNoteStage.REVIEWING, vm.uiState.value.stage)
    }

    @Test
    fun rewordingBeforeSavingGoesBackToTheNote() = run(CountingDrafts { draftOf(groceries) }) { vm, _ ->
        vm.onNoteChange("note")
        vm.interpret()
        vm.editNote()

        assertEquals(QuickNoteStage.WRITING, vm.uiState.value.stage)
        assertEquals("note", vm.uiState.value.note)
        assertTrue(vm.uiState.value.proposals.isEmpty())
    }

    @Test
    fun aPendingSaveCannotBeAbandonedByRewordingTheNote() {
        val gate = CompletableDeferred<Unit>()
        val delayed = object : LedgerRepository by ledger {
            override suspend fun saveTransaction(transaction: Transaction) {
                gate.await()
                ledger.saveTransaction(transaction)
            }
        }
        run(CountingDrafts { draftOf(groceries, bill) }, delayed) { vm, drafts ->
            vm.onNoteChange("note")
            vm.interpret()
            vm.confirm(0)
            assertTrue(vm.uiState.value.isSaving)
            assertFalse(vm.uiState.value.canEditNote)
            vm.editNote()
            vm.interpret()
            assertEquals(QuickNoteStage.REVIEWING, vm.uiState.value.stage)
            assertEquals(1, drafts.calls)
            gate.complete(Unit)
            assertEquals(ProposalStatus.SAVED, vm.uiState.value.proposals[0].status)
            assertFalse(vm.uiState.value.canEditNote)
        }
    }

    // --- Refunds -------------------------------------------------------------------------------

    @Test
    fun aRefundOffersThePurchasesAndSavesAgainstTheOnePicked() = run(CountingDrafts {
        draftOf(DraftProposal.ActualTransaction(TransactionType.REFUND, lkr(500), null, today))
    }) { vm, _ ->
        ledger.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(2_300), account = bank))
        vm.onNoteChange("note")
        vm.interpret()
        assertTrue(ProposalBlocker.PURCHASE_MISSING in vm.uiState.value.proposals[0].blockers)

        vm.openEditor(0)
        assertEquals(listOf("p1"), vm.uiState.value.purchases.map { it.id.value })

        val purchase = vm.uiState.value.purchases.single().current
        vm.update(0) { (it as ProposalInput.Actual).copy(refundOf = purchase.id, accountId = purchase.accountId) }
        assertTrue(vm.uiState.value.proposals[0].blockers.isEmpty())
        vm.confirm(0)
        assertEquals("p1", ledger.transactions.values.single { it.type == TransactionType.REFUND }.refundOf?.value)
    }

    // --- Failures ------------------------------------------------------------------------------

    @Test
    fun aFailedReadKeepsTheNoteAndOnlyAnotherTapReadsAgain() {
        var fail = true
        run(CountingDrafts { if (fail) throw QuickNoteDraftError.TimedOut() else draftOf(groceries) }) { vm, drafts ->
            vm.onNoteChange("paid 1250 at keells")
            vm.interpret()

            assertEquals(QuickNoteFailure.TIMED_OUT, vm.uiState.value.failure)
            assertEquals("paid 1250 at keells", vm.uiState.value.note)
            assertEquals(QuickNoteStage.WRITING, vm.uiState.value.stage)
            assertEquals("Nothing retries on its own", 1, drafts.calls)

            fail = false
            vm.interpret()
            assertEquals(2, drafts.calls)
            assertEquals(QuickNoteStage.REVIEWING, vm.uiState.value.stage)
        }
    }

    @Test
    fun theFailureKindsAreTold() {
        val cases = mapOf(
            QuickNoteDraftError.RateLimited() to QuickNoteFailure.RATE_LIMITED,
            QuickNoteDraftError.ServiceUnavailable() to QuickNoteFailure.UNAVAILABLE,
            QuickNoteDraftError.InvalidResponse() to QuickNoteFailure.INVALID_RESPONSE,
            QuickNoteDraftError.AppNotVerified() to QuickNoteFailure.APP_NOT_VERIFIED,
            QuickNoteDraftError.Offline() to QuickNoteFailure.UNAVAILABLE,
        )
        cases.forEach { (error, expectedFailure) ->
            run(CountingDrafts { throw error }) { vm, _ ->
                vm.onNoteChange("note")
                vm.interpret()
                assertEquals(expectedFailure, vm.uiState.value.failure)
            }
        }
    }

    @Test
    fun aNoteWithNothingInItSaysSoInsteadOfOpeningAnEmptyReview() = run(CountingDrafts { draftOf() }) { vm, _ ->
        vm.onNoteChange("hello")
        vm.interpret()

        assertEquals(QuickNoteFailure.NOTHING_FOUND, vm.uiState.value.failure)
        assertEquals(QuickNoteStage.WRITING, vm.uiState.value.stage)
    }

    @Test
    fun cancellingReadingGoesBackToTheNote() {
        val gate = CompletableDeferred<Draft>()
        run(CountingDrafts { gate.await() }) { vm, _ ->
            vm.onNoteChange("note")
            vm.interpret()
            vm.cancelReading()

            assertEquals(QuickNoteStage.WRITING, vm.uiState.value.stage)
            assertEquals("note", vm.uiState.value.note)
            assertNull(vm.uiState.value.failure)
        }
    }
}
