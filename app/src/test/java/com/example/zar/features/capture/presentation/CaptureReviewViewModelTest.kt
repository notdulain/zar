package com.example.zar.features.capture.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.OCT_10
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
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.DraftRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.repository.LinkWrite
import com.example.zar.features.capture.domain.usecase.ConfirmCapturedDraftUseCase
import com.example.zar.features.capture.domain.usecase.DiscardCapturedDraftUseCase
import com.example.zar.features.capture.domain.usecase.LinkCapturedDraftUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCaptureAccountsUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCaptureLedgerUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCapturedDraftsUseCase
import com.example.zar.features.capture.presentation.viewmodel.CaptureMessage
import com.example.zar.features.capture.presentation.viewmodel.CaptureReviewViewModel
import com.example.zar.features.capture.presentation.viewmodel.SettleKind
import com.example.zar.features.capture.presentation.viewmodel.SettleStage
import com.example.zar.features.rules.FakeCategoryRules
import com.example.zar.features.transactions.presentation.activity.FakeAccounts
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureReviewViewModelTest {
    private val now = Instant.parse("2026-10-12T05:00:00Z")
    private val window = 5_000L

    private class Drafts(val ledger: FakeLedgerRepository) : DraftRepository {
        val values = MutableStateFlow<List<Draft>>(emptyList())
        val links = mutableListOf<EvidenceLink>()
        var linkWrite = LinkWrite.SAVED
        var failLink = false
        override fun observePendingDrafts(): Flow<List<Draft>> = values.map { it.filter { d -> d.status == DraftStatus.PENDING_REVIEW } }
        override fun observeDrafts(): Flow<List<Draft>> = values
        override suspend fun saveDraft(draft: Draft): Draft { values.value = values.value + draft; return draft }
        override suspend fun confirmCapturedDraft(draft: Draft, transaction: Transaction) {
            ledger.saveTransaction(transaction)
            close(draft.id)
        }
        override suspend fun setStatus(id: DraftId, status: DraftStatus) = close(id, status)
        override suspend fun closeAsLinked(draft: Draft, link: EvidenceLink): LinkWrite {
            if (failLink) throw IOException("Offline")
            links += link
            ledger.linkEvidence(link)
            close(draft.id)
            return linkWrite
        }
        private fun close(id: DraftId, status: DraftStatus = DraftStatus.CONFIRMED) {
            values.value = values.value.map { if (it.id == id) it.copy(status = status) else it }
        }
    }

    private val ledger = FakeLedgerRepository()
    private val drafts = Drafts(ledger)
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val created = mutableListOf<CaptureReviewViewModel>()

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun teardown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun purchaseDraft(id: String = "d1", direction: Direction? = Direction.OUT, amount: Long = 800) = Draft(
        DraftId(id), DraftSource.SMS,
        listOf(
            DraftProposal.ActualTransaction(
                null, lkr(amount), null, OCT_10, merchant = "CAFE SEVENTY THREE MALABE LK",
                uncertainFields = setOf(DraftField.TYPE), direction = direction,
            ),
        ),
        DraftStatus.PENDING_REVIEW, T0,
    )

    private fun viewModel(
        draftId: String = "d1",
        ledgerRepository: LedgerRepository = ledger,
    ): CaptureReviewViewModel = CaptureReviewViewModel(
        SavedStateHandle(mapOf("draftId" to draftId)),
        ObserveCapturedDraftsUseCase(drafts),
        ObserveCaptureAccountsUseCase(accounts),
        ObserveCaptureLedgerUseCase(ledgerRepository),
        ConfirmCapturedDraftUseCase(drafts, FakeCategoryRules().observeUseCase()),
        DiscardCapturedDraftUseCase(drafts),
        LinkCapturedDraftUseCase(drafts, ledgerRepository),
        { now },
        window,
    ).also { created += it }

    private suspend fun earlierPurchase(id: String = "earlier", merchant: String = "Cafe Seventy Three") =
        ledger.saveTransaction(tx(id, TransactionType.EXPENSE, lkr(800), bank, OCT_10).copy(merchant = merchant))

    private suspend fun transfer(id: String = "t1") {
        ledger.saveTransaction(tx("$id-out", TransactionType.TRANSFER_OUT, lkr(25_000), cash, OCT_10, transferId = id))
        ledger.saveTransaction(tx("$id-in", TransactionType.TRANSFER_IN, lkr(25_000), bank, OCT_10, transferId = id))
    }

    // --- Possible duplicates ---------------------------------------------------------------

    @Test
    fun anEarlierEntryWithTheSameAmountIsOfferedAndNothingChangesYet() = runTest {
        earlierPurchase()
        drafts.saveDraft(purchaseDraft())
        val vm = viewModel(); runCurrent()

        val state = vm.uiState.value
        assertEquals(listOf(TransactionId("earlier")), state.duplicates.map { it.entry.id })
        assertTrue(drafts.links.isEmpty())
        assertEquals(1, ledger.transactions.size)
    }

    @Test
    fun sayingItIsTheSamePaymentWaitsForTheUndoWindowThenAttachesTheMessageWithoutPostingMoney() = runTest {
        earlierPurchase()
        drafts.saveDraft(purchaseDraft())
        val vm = viewModel(); runCurrent()

        vm.onSamePayment()
        assertEquals(SettleStage.UNDO_WINDOW, vm.uiState.value.settlement?.stage)
        assertTrue("nothing is written inside the window", drafts.links.isEmpty())

        advanceTimeBy(window + 1); runCurrent()

        assertEquals(SettleStage.SAVED, vm.uiState.value.settlement?.stage)
        assertEquals(listOf(EvidenceLink(com.example.zar.core.domain.model.EvidenceId("d1"), TransactionId("earlier"), now)), drafts.links)
        assertEquals("no money was posted", 1, ledger.transactions.size)
        assertNotNull("what was decided stays on screen after the draft closes", vm.uiState.value.draft)
    }

    @Test
    fun takingTheAnswerBackInsideTheWindowWritesNothingAndOffersTheEntryAgain() = runTest {
        earlierPurchase()
        drafts.saveDraft(purchaseDraft())
        val vm = viewModel(); runCurrent()
        vm.onSamePayment()

        vm.onUndo()
        advanceTimeBy(window * 2); runCurrent()

        assertNull(vm.uiState.value.settlement)
        assertTrue(drafts.links.isEmpty())
        assertEquals(DraftStatus.PENDING_REVIEW, drafts.values.value.single().status)
        assertEquals(1, vm.uiState.value.duplicates.size)
    }

    @Test
    fun doneWritesAWaitingAnswerAtOnceAndLeaves() = runTest {
        earlierPurchase()
        drafts.saveDraft(purchaseDraft())
        val vm = viewModel(); runCurrent()
        vm.onSamePayment()

        vm.onFinish(); runCurrent()

        assertEquals(1, drafts.links.size)
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun goingBackWithoutAnAnswerJustLeavesAndWritesNothing() = runTest {
        drafts.saveDraft(purchaseDraft())
        val vm = viewModel(); runCurrent()

        vm.onFinish()

        assertTrue(vm.uiState.value.isFinished)
        assertTrue(drafts.links.isEmpty())
    }

    @Test
    fun aDifferentPurchaseIsNotOfferedAgainAndCanBeSavedAsANewEntry() = runTest {
        earlierPurchase()
        drafts.saveDraft(purchaseDraft())
        val vm = viewModel(); runCurrent()

        vm.onKeepApart()
        assertTrue(vm.uiState.value.duplicates.isEmpty())
        vm.onAccountSelect(bank.id); vm.onTypeSelect(TransactionType.EXPENSE); vm.onSpendingClassSelect(SpendingClass.DISCRETIONARY)
        vm.confirm(); runCurrent()

        assertEquals(setOf("earlier", "draft-d1-0"), ledger.transactions.keys.map { it.value }.toSet())
        assertTrue(drafts.links.isEmpty())
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun theAnswerCannotBeChangedWhileItIsWaitingToBeWritten() = runTest {
        earlierPurchase()
        drafts.saveDraft(purchaseDraft())
        val vm = viewModel(); runCurrent()
        vm.onSamePayment()

        vm.onTypeSelect(TransactionType.INCOME); vm.onAccountSelect(cash.id); vm.onKeepApart(); vm.onTransferSelect(); vm.confirm(); vm.discard()
        runCurrent()

        val state = vm.uiState.value
        assertNull(state.type); assertNull(state.accountId); assertFalse(state.asTransfer)
        assertEquals(DraftStatus.PENDING_REVIEW, drafts.values.value.single().status)
    }

    @Test
    fun anotherWaitingMessageForTheSamePaymentIsMentioned() = runTest {
        drafts.saveDraft(purchaseDraft("d1"))
        drafts.saveDraft(purchaseDraft("d2"))
        val vm = viewModel(); runCurrent()

        assertEquals(listOf("d2"), vm.uiState.value.twins.map { it.id.value })
    }

    // --- Offline, failure and retry --------------------------------------------------------

    @Test
    fun aWriteStoredOnThePhoneWhileOfflineIsSavedAndSaysSo() = runTest {
        earlierPurchase()
        drafts.saveDraft(purchaseDraft())
        drafts.linkWrite = LinkWrite.QUEUED
        val vm = viewModel(); runCurrent()
        vm.onSamePayment()

        advanceTimeBy(window + 1); runCurrent()
        assertEquals(SettleStage.QUEUED, vm.uiState.value.settlement?.stage)
        assertFalse(vm.uiState.value.isFinished)

        vm.onFinish()
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun aFailedWriteKeepsTheAnswerAndRetryingSavesItOnce() = runTest {
        earlierPurchase()
        drafts.saveDraft(purchaseDraft())
        val vm = viewModel(); runCurrent()
        vm.onSamePayment()
        drafts.failLink = true

        advanceTimeBy(window + 1); runCurrent()
        assertEquals(SettleStage.FAILED, vm.uiState.value.settlement?.stage)
        assertEquals(CaptureMessage.FAILED, vm.uiState.value.message)
        assertEquals(DraftStatus.PENDING_REVIEW, drafts.values.value.single().status)

        drafts.failLink = false
        vm.onRetry(); runCurrent()
        assertEquals(SettleStage.SAVED, vm.uiState.value.settlement?.stage)
        assertEquals(1, drafts.links.size)
    }

    @Test
    fun afterAFailureTheChoiceCanBeTakenBack() = runTest {
        earlierPurchase()
        drafts.saveDraft(purchaseDraft())
        drafts.failLink = true
        val vm = viewModel(); runCurrent()
        vm.onSamePayment()
        advanceTimeBy(window + 1); runCurrent()

        vm.onUndo()

        assertNull(vm.uiState.value.settlement)
        assertNull(vm.uiState.value.message)
        assertTrue(drafts.links.isEmpty())
    }

    @Test
    fun whenTheEntriesCannotBeReadTheReviewStillWorksAndSaysItCouldNotCheck() = runTest {
        drafts.saveDraft(purchaseDraft())
        val unreadable = object : LedgerRepository by ledger {
            override fun observeLedger() = flow<List<com.example.zar.core.domain.model.LedgerEntry>> { throw IOException("No cache") }
        }
        val vm = viewModel(ledgerRepository = unreadable); runCurrent()

        val state = vm.uiState.value
        assertTrue(state.matchesUnavailable)
        assertTrue(state.duplicates.isEmpty())
        vm.onAccountSelect(bank.id); vm.onTypeSelect(TransactionType.EXPENSE); vm.confirm(); runCurrent()
        assertTrue(vm.uiState.value.isFinished)
    }

    // --- Transfers -------------------------------------------------------------------------

    @Test
    fun aCreditThatIsATransferIsLinkedToTheTransferAlreadyInTheLedger() = runTest {
        transfer()
        drafts.saveDraft(purchaseDraft(direction = Direction.IN, amount = 25_000))
        val vm = viewModel(); runCurrent()
        vm.onAccountSelect(bank.id)

        vm.onTransferSelect()
        val state = vm.uiState.value
        assertTrue(state.asTransfer)
        assertEquals(listOf("t1-in"), state.transferMatches.map { it.leg.id.value })
        assertEquals("only one fits, so it is chosen", TransactionId("t1-in"), state.transferChoice)

        vm.onLinkTransfer()
        assertEquals(SettleKind.TRANSFER, vm.uiState.value.settlement?.kind)
        vm.onFinish(); runCurrent()

        assertEquals(listOf(TransactionId("t1-in")), drafts.links.map { it.transactionId })
        assertEquals("no money was posted again", 2, ledger.transactions.size)
    }

    @Test
    fun withSeveralMatchingTransfersNothingIsChosenForThePerson() = runTest {
        transfer("t1"); transfer("t2")
        drafts.saveDraft(purchaseDraft(direction = Direction.IN, amount = 25_000))
        val vm = viewModel(); runCurrent()

        vm.onTransferSelect()
        assertEquals(2, vm.uiState.value.transferMatches.size)
        assertNull(vm.uiState.value.transferChoice)

        vm.onLinkTransfer()
        assertNull("no link without a choice", vm.uiState.value.settlement)

        vm.onTransferChoice(TransactionId("t2-in")); vm.onLinkTransfer()
        assertEquals(TransactionId("t2-in"), vm.uiState.value.settlement?.target?.id)
    }

    @Test
    fun whenNoTransferMatchesThereIsNothingToLink() = runTest {
        drafts.saveDraft(purchaseDraft(direction = Direction.IN, amount = 25_000))
        val vm = viewModel(); runCurrent()

        vm.onTransferSelect()
        vm.onLinkTransfer()

        assertTrue(vm.uiState.value.transferMatches.isEmpty())
        assertNull(vm.uiState.value.settlement)
        assertTrue(drafts.links.isEmpty())
    }

    @Test
    fun choosingExpenseOrIncomeAgainLeavesTheTransferChoice() = runTest {
        transfer()
        drafts.saveDraft(purchaseDraft(direction = Direction.IN, amount = 25_000))
        val vm = viewModel(); runCurrent()
        vm.onTransferSelect()

        vm.onTypeSelect(TransactionType.INCOME)

        assertFalse(vm.uiState.value.asTransfer)
        assertNull(vm.uiState.value.transferChoice)
        assertEquals(TransactionType.INCOME, vm.uiState.value.type)
    }

    @Test
    fun aCreditIsNeverAssumedToBeIncomeOrATransfer() = runTest {
        transfer()
        drafts.saveDraft(purchaseDraft(direction = Direction.IN, amount = 25_000))
        val vm = viewModel(); runCurrent()

        val state = vm.uiState.value
        assertNull(state.type)
        assertFalse(state.asTransfer)
        vm.confirm()
        assertTrue("saving needs a type", vm.uiState.value.showErrors)
        assertTrue(ledger.transactions.size == 2)
    }
}
