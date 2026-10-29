package com.example.zar.integration

import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.data.remote.firestore.LedgerDocumentMapper
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.EvidenceId
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.repository.LinkWrite
import com.example.zar.features.auth.EmulatorFirebase
import com.example.zar.features.auth.step
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.example.zar.features.capture.domain.usecase.CaptureOutcome
import com.example.zar.features.capture.domain.usecase.CaptureTextUseCase
import com.example.zar.features.capture.domain.usecase.ConfirmCapturedDraftUseCase
import com.example.zar.features.capture.domain.usecase.LinkCapturedDraftUseCase
import com.example.zar.features.quicknote.domain.review.QuickNoteReviewRules
import com.example.zar.features.rules.data.FirestoreCategoryRuleRepository
import com.example.zar.features.rules.domain.usecase.ObserveCategoryRulesUseCase
import com.example.zar.features.transactions.domain.activity.usecase.CorrectExpenseUseCase
import com.example.zar.features.transactions.domain.manual.ManualEntryRules
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseDetails
import com.example.zar.features.transactions.domain.manual.model.TransferDetails
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SCRUM-21 / D08: offline edits, reconnection, replay, corrections and undo across the manual, quick-note and
 * capture paths. Every case uses the production Firestore repositories, domain use cases and projections on the
 * local Auth + Firestore emulators (project demo-zar) and then reads what the server stored with `Source.SERVER`.
 * Money is exact minor units (LKR 500.00 = 50_000). Test fixture data only.
 */
class OfflineLedgerIntegrationTest {

    @Before fun requireLocalEmulators() = EmulatorFirebase.requireEmulators()

    private val lkr = Currencies.LKR
    private fun lkr(minor: Long) = FiatMoney(minor, lkr)

    /** What the manual form saves, built by the rules the form uses. Millisecond-exact so equality survives storage. */
    private fun expense(account: Account, minor: Long, id: String = "exp-${UUID.randomUUID()}", title: String = "Lunch"): Transaction =
        ManualEntryRules.newExpense(
            TransactionId(id),
            ExpenseDetails(
                account = account,
                amount = lkr(minor),
                date = ZarTime.today(),
                category = ExpenseCategory.COFFEE_DINING,
                spendingClass = SpendingClass.DISCRETIONARY,
                title = title,
                description = null,
                time = LocalTime.of(13, 5),
            ),
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
        )

    private fun spent(h: IntegrationHarness, entries: List<LedgerEntry>) = h.summaryToday(entries).spent.total.amount

    private suspend fun IntegrationHarness.localUntil(label: String, predicate: (List<LedgerEntry>) -> Boolean): List<LedgerEntry> =
        step(label) { ledger.observeLedger().first { predicate(it) } }

    /** The phone and the server agree on every entry's current value, void state and applied corrections. */
    private fun assertSameLedger(local: List<LedgerEntry>, server: List<LedgerEntry>) {
        fun view(e: LedgerEntry) = Triple(e.current, e.isVoided, e.corrections.map { it.id })
        assertEquals(local.sortedBy { it.id.value }.map(::view), server.sortedBy { it.id.value }.map(::view))
    }

    @Test
    fun offlineSaveThenOfflineCorrectionReachTheServerOnceAndEveryProjectionAgrees() = runBlocking {
        val h = IntegrationHarness.open("offline-correction")
        try {
            val bank = h.seedAccount("bank")
            val original = expense(bank, 50_000)
            h.offline()

            h.saveTransaction(original, bank).getOrThrow()
            val saved = h.localUntil("saved entry shown offline") { list -> list.any { it.id == original.id } }.single()
            h.correct(saved, TransactionChanges(amount = lkr(65_000)), CorrectionId("corr-${UUID.randomUUID()}"), Instant.now()).getOrThrow()
            val local = h.localUntil("corrected entry shown offline") { list -> list.singleOrNull()?.isCorrected == true }

            // While still offline the phone already shows one entry, the corrected value, and consistent totals.
            assertEquals(lkr(65_000), local.single().current.amount)
            assertEquals(lkr(50_000), local.single().original.amount)
            assertEquals(lkr(935_000), h.balance(bank, local).current)
            assertEquals(lkr(65_000), spent(h, local))

            h.online()
            h.flush()

            // Server: the original is untouched, exactly one correction exists, nothing is voided.
            val transactions = h.serverDocs(FirestoreCollections.TRANSACTIONS)
            assertEquals(1, transactions.size)
            assertEquals(original, LedgerDocumentMapper.transactionFromMap(transactions.single().data!!))
            assertEquals(1, h.serverDocs(FirestoreCollections.CORRECTIONS).size)
            assertEquals(0, h.serverDocs(FirestoreCollections.VOIDS).size)

            val server = h.serverLedger()
            assertSameLedger(h.localLedger(), server)
            assertEquals(lkr(935_000), h.balance(bank, server).current)
            assertEquals(lkr(65_000), spent(h, server))
            assertTrue(h.ledger.observeRejectedWrites().first().isEmpty())
        } finally { h.close() }
    }

    @Test
    fun offlineCorrectionsOfASyncedExpenseApplyOldestFirstAndAReplayedCorrectionIdChangesNothing() = runBlocking {
        val h = IntegrationHarness.open("correction-replay")
        try {
            val bank = h.seedAccount("bank")
            val original = expense(bank, 50_000)
            h.saveTransaction(original, bank).getOrThrow()
            h.flush()
            val synced = h.localLedger().single()

            val first = CorrectionId("corr-a-${UUID.randomUUID()}")
            val second = CorrectionId("corr-b-${UUID.randomUUID()}")
            val t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            h.offline()
            h.correct(synced, TransactionChanges(amount = lkr(60_000)), first, t0).getOrThrow()
            val afterFirst = h.localUntil("first correction shown") { list -> list.single().corrections.size == 1 }.single()
            h.correct(afterFirst, TransactionChanges(amount = lkr(70_000)), second, t0.plusSeconds(1)).getOrThrow()
            h.localUntil("both corrections shown") { list -> list.single().corrections.size == 2 }
            h.online()
            h.flush()

            assertEquals(listOf(first, second).map { it.value }.toSet(), h.serverDocs(FirestoreCollections.CORRECTIONS).map { it.id }.toSet())
            assertEquals(lkr(70_000), h.serverLedger().single().current.amount)

            // The same correction ID replayed with different content (a stale retry) must not overwrite the stored one.
            h.ledger.addCorrection(Correction(first, original.id, TransactionChanges(amount = lkr(99_900)), t0.plusSeconds(5)))
            h.flush()
            delay(500)
            val corrections = h.serverDocs(FirestoreCollections.CORRECTIONS)
            assertEquals(2, corrections.size)
            assertEquals(
                lkr(60_000),
                LedgerDocumentMapper.correctionFromMap(corrections.single { it.id == first.value }.data!!)!!.changes.amount,
            )
            val server = h.serverLedger()
            assertEquals(lkr(70_000), server.single().current.amount)
            assertEquals(lkr(930_000), h.balance(bank, server).current)
            assertEquals(lkr(70_000), spent(h, server))
            assertSameLedger(h.localLedger(), server)
            assertEquals(original, server.single().original)
        } finally { h.close() }
    }

    @Test
    fun aReplayedSaveWithTheSameIdKeepsTheOriginalAndIsCountedOnce() = runBlocking {
        val h = IntegrationHarness.open("save-replay")
        try {
            val bank = h.seedAccount("bank")
            val original = expense(bank, 50_000)
            h.saveTransaction(original, bank).getOrThrow()
            h.flush()

            // A stale retry that carries different content under the same ID, then an offline double-tap of a new ID.
            h.saveTransaction(original.copy(amount = lkr(90_000)), bank).getOrThrow()
            h.flush()
            val retried = expense(bank, 20_000)
            h.offline()
            h.saveTransaction(retried, bank).getOrThrow()
            h.saveTransaction(retried, bank).getOrThrow()
            h.online()
            h.flush()
            delay(500)

            val docs = h.serverDocs(FirestoreCollections.TRANSACTIONS)
            assertEquals(setOf(original.id.value, retried.id.value), docs.map { it.id }.toSet())
            assertEquals(lkr(50_000), LedgerDocumentMapper.transactionFromMap(docs.single { it.id == original.id.value }.data!!)!!.amount)

            val server = h.serverLedger()
            val local = h.localLedger()
            assertSameLedger(local, server)
            assertEquals(lkr(930_000), h.balance(bank, server).current)
            assertEquals(lkr(70_000), spent(h, server))
            assertEquals(lkr(70_000), spent(h, local))
            // Nothing was lost, so nothing is reported as refused.
            assertTrue(h.ledger.observeRejectedWrites().first().isEmpty())
        } finally { h.close() }
    }

    @Test
    fun anOfflineReplayWithChangedContentSettlesBackToTheStoredOriginalAfterReconnecting() = runBlocking {
        val h = IntegrationHarness.open("offline-replay")
        try {
            val bank = h.seedAccount("bank")
            val original = expense(bank, 50_000)
            h.saveTransaction(original, bank).getOrThrow()
            h.flush()

            // A retry made while offline is not asserted while pending; once the server has refused it, the ledger,
            // balance and spending go back to the stored original and the refusal is not reported as a lost save.
            h.offline()
            h.saveTransaction(original.copy(amount = lkr(90_000)), bank).getOrThrow()
            h.online()
            h.flush()
            val settled = h.localUntil("replay refused and rolled back") { list -> list.single().current.amount == lkr(50_000) }
            assertEquals(original, settled.single().original)
            val server = h.serverLedger()
            assertEquals(lkr(50_000), server.single().current.amount)
            assertEquals(lkr(950_000), h.balance(bank, settled).current)
            assertEquals(lkr(50_000), spent(h, settled))
            assertSameLedger(settled, server)
            delay(500)
            assertTrue(h.ledger.observeRejectedWrites().first().isEmpty())
        } finally { h.close() }
    }

    @Test
    fun anOfflineUndoRemovesTheEntryFromBalanceAndSpendingAndALateCorrectionCannotReviveIt() = runBlocking {
        val h = IntegrationHarness.open("offline-undo")
        try {
            val bank = h.seedAccount("bank")
            val original = expense(bank, 50_000)
            h.saveTransaction(original, bank).getOrThrow()
            h.flush()
            val synced = h.localLedger().single()
            assertEquals(lkr(950_000), h.balance(bank, listOf(synced)).current)

            h.offline()
            val voidedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            h.void(synced, voidedAt, "Entered twice").getOrThrow()
            val local = h.localUntil("undo shown offline") { list -> list.single().isVoided }
            assertEquals(lkr(1_000_000), h.balance(bank, local).current)
            assertEquals(lkr(0), spent(h, local))
            h.online()
            h.flush()

            // The record is never deleted: the original stays, a single void marker is added.
            assertEquals(original, LedgerDocumentMapper.transactionFromMap(h.serverDocs(FirestoreCollections.TRANSACTIONS).single().data!!))
            assertEquals(1, h.serverDocs(FirestoreCollections.VOIDS).size)

            // Undoing twice and a stale correction from another phone change nothing.
            h.void(synced, voidedAt.plusSeconds(60), "again").getOrThrow()
            h.ledger.addCorrection(
                Correction(CorrectionId("late-${UUID.randomUUID()}"), original.id, TransactionChanges(amount = lkr(80_000)), voidedAt.plusSeconds(90)),
            )
            h.flush()
            delay(500)
            assertEquals(1, h.serverDocs(FirestoreCollections.VOIDS).size)
            val storedVoid = LedgerDocumentMapper.voidFromMap(h.serverDocs(FirestoreCollections.VOIDS).single().data!!)!!
            assertEquals(VoidMarker(original.id, voidedAt, "Entered twice"), storedVoid)

            val server = h.serverLedger()
            assertTrue(server.single().isVoided)
            assertEquals(lkr(1_000_000), h.balance(bank, server).current)
            assertEquals(lkr(0), spent(h, server))
            assertTrue(h.localLedger().active().isEmpty())
            // A void marker on an undone entry also refuses further corrections through the use case.
            assertTrue(h.correct(server.single(), TransactionChanges(amount = lkr(10_000)), CorrectionId("x-${UUID.randomUUID()}"), Instant.now()).isFailure)
        } finally { h.close() }
    }

    private val sms = "Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111."

    @Test
    fun aCapturedMessageLinkedOfflineAddsNoMoneyAndUndoingTheEntryStillRemovesItAndTheEvidenceStaysSingle() = runBlocking {
        val h = IntegrationHarness.open("capture-link")
        try {
            val bank = h.seedAccount("bank", openingDate = LocalDate.of(2026, 10, 1))
            val manual = expense(bank, 80_000, title = "Cafe")
            h.saveTransaction(manual, bank).getOrThrow()
            h.flush()

            val capture = CaptureTextUseCase(BankSmsParser(), h.drafts)
            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            val drafted = capture(sms, DraftSource.SHARED_TEXT, now).getOrThrow() as CaptureOutcome.Drafted
            // Capturing the same text again is the same draft, not a second one.
            assertTrue(capture(sms, DraftSource.SHARED_TEXT, now.plusSeconds(30)).getOrThrow() is CaptureOutcome.Drafted)
            h.flush()
            assertEquals(1, h.serverDocs(FirestoreCollections.DRAFTS).size)
            val draft = step("read draft") { h.drafts.observeDrafts().first { list -> list.any { it.id == drafted.draftId } } }.single { it.id == drafted.draftId }

            h.offline()
            val link = LinkCapturedDraftUseCase(h.drafts, h.ledger)
            assertEquals(LinkWrite.QUEUED, link(draft, manual.id, now.plusSeconds(60)).getOrThrow())
            val local = h.localUntil("evidence shown offline") { list -> list.single().evidenceIds.isNotEmpty() }
            assertEquals(listOf(EvidenceId(draft.id.value)), local.single().evidenceIds)
            assertEquals(lkr(920_000), h.balance(bank, local).current)
            assertEquals(lkr(80_000), spent(h, local))
            h.online()
            h.flush()

            // Server: still one transaction, one evidence link pointing at it, and the draft is closed.
            assertEquals(1, h.serverDocs(FirestoreCollections.TRANSACTIONS).size)
            val links = h.serverDocs(FirestoreCollections.EVIDENCE_LINKS)
            assertEquals(1, links.size)
            assertEquals(manual.id, LedgerDocumentMapper.evidenceLinkFromMap(links.single().data!!)!!.transactionId)
            assertEquals(DraftStatus.CONFIRMED.name, h.serverDocs(FirestoreCollections.DRAFTS).single().getString("status"))
            val server = h.serverLedger()
            assertEquals(listOf(EvidenceId(draft.id.value)), server.single().evidenceIds)
            assertEquals(lkr(920_000), h.balance(bank, server).current)
            assertEquals(lkr(80_000), spent(h, server)) // counted once, not 160_000

            // Replaying the link and the capture creates nothing new.
            assertEquals(LinkWrite.SAVED, h.drafts.closeAsLinked(draft, EvidenceLink(EvidenceId(draft.id.value), manual.id, now.plusSeconds(60))))
            assertEquals(1, h.serverDocs(FirestoreCollections.EVIDENCE_LINKS).size)
            assertTrue(capture(sms, DraftSource.SHARED_TEXT, now.plusSeconds(90)).getOrThrow() is CaptureOutcome.AlreadyReviewed)
            h.flush()
            assertEquals(1, h.serverDocs(FirestoreCollections.DRAFTS).size)

            // Undo: the linked entry leaves balance and spending; the evidence does not post money back.
            h.void(server.single(), now.plusSeconds(120)).getOrThrow()
            h.flush()
            val afterUndo = h.serverLedger()
            assertTrue(afterUndo.single().isVoided)
            assertEquals(lkr(1_000_000), h.balance(bank, afterUndo).current)
            assertEquals(lkr(0), spent(h, afterUndo))
            assertEquals(1, h.serverDocs(FirestoreCollections.TRANSACTIONS).size)
            assertTrue(h.localLedger().active().isEmpty())
        } finally { h.close() }
    }

    @Test
    fun aConfirmedCaptureCanBeCorrectedAndUndoneAndConfirmingAgainSavesOnce() = runBlocking {
        val h = IntegrationHarness.open("capture-confirm")
        try {
            val bank = h.seedAccount("bank", openingDate = LocalDate.of(2026, 10, 1))
            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            val capture = CaptureTextUseCase(BankSmsParser(), h.drafts)
            val drafted = capture(sms, DraftSource.SHARED_TEXT, now).getOrThrow() as CaptureOutcome.Drafted
            val draft = step("read draft") { h.drafts.observeDrafts().first { list -> list.any { it.id == drafted.draftId } } }.single { it.id == drafted.draftId }
            val confirm = ConfirmCapturedDraftUseCase(h.drafts, ObserveCategoryRulesUseCase(FirestoreCategoryRuleRepository(h.db, h.session)))

            confirm(draft, bank, TransactionType.EXPENSE, SpendingClass.UNKNOWN, now).getOrThrow()
            confirm(draft, bank, TransactionType.EXPENSE, SpendingClass.UNKNOWN, now.plusSeconds(5)).getOrThrow()
            h.flush()

            val stored = h.serverDocs(FirestoreCollections.TRANSACTIONS)
            assertEquals(1, stored.size)
            val captured = LedgerDocumentMapper.transactionFromMap(stored.single().data!!)!!
            assertEquals(lkr(80_000), captured.amount)
            assertEquals(lkr(920_000), h.balance(bank, h.serverLedger()).current)

            // Correct offline (the imported fact is kept), reconnect, then undo.
            val entry = h.localLedger().single()
            h.offline()
            CorrectExpenseUseCase(h.correct)(entry, TransactionChanges(amount = lkr(85_000)), CorrectionId("fix-${UUID.randomUUID()}"), now.plusSeconds(30)).getOrThrow()
            val corrected = h.localUntil("correction shown") { list -> list.single().isCorrected }
            assertEquals(lkr(915_000), h.balance(bank, corrected).current)
            h.online()
            h.flush()
            val server = h.serverLedger()
            assertEquals(captured, server.single().original)
            assertEquals(lkr(85_000), server.single().current.amount)
            assertEquals(lkr(85_000), spent(h, server))
            assertEquals(lkr(915_000), h.balance(bank, server).current)

            h.void(server.single(), now.plusSeconds(60)).getOrThrow()
            h.flush()
            val afterUndo = h.serverLedger()
            assertEquals(lkr(1_000_000), h.balance(bank, afterUndo).current)
            assertEquals(lkr(0), spent(h, afterUndo))
            assertSameLedger(h.localLedger(), afterUndo)
        } finally { h.close() }
    }

    @Test
    fun aQuickNoteSavesOnlyTheConfirmedProposalOnceKeepsExpectedSeparateAndUndoRestoresTheBalance() = runBlocking {
        val h = IntegrationHarness.open("quick-note")
        try {
            val bank = h.seedAccount("bank")
            val today = ZarTime.today()
            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            val draft = Draft(
                id = DraftId("note-${UUID.randomUUID()}"),
                source = DraftSource.QUICK_NOTE,
                proposals = listOf(
                    DraftProposal.ActualTransaction(
                        type = TransactionType.EXPENSE, amount = lkr(120_000), accountId = bank.id, date = today,
                        spendingClass = SpendingClass.DISCRETIONARY, title = "Groceries",
                    ),
                    DraftProposal.Expected(
                        direction = Direction.OUT, amount = lkr(300_000), dueDate = today.plusDays(5), title = "Electricity", accountId = bank.id,
                    ),
                ),
                status = DraftStatus.PENDING_REVIEW,
                createdAt = now,
            )
            h.drafts.saveDraft(draft)
            h.flush()

            // Nothing is money until the person confirms a proposal.
            assertEquals(0, h.serverDocs(FirestoreCollections.TRANSACTIONS).size)
            assertEquals(0, h.serverDocs(FirestoreCollections.EXPECTED_PAYMENTS).size)
            assertEquals(lkr(1_000_000), h.balance(bank, h.localLedger()).current)

            // Confirm the actual one offline, twice (a retry), then reconnect.
            val actual = QuickNoteReviewRules.inputFrom(draft.proposals[0])
            h.offline()
            h.confirmQuickNote(draft, 0, actual, today, now).getOrThrow()
            h.confirmQuickNote(draft, 0, actual, today, now.plusSeconds(2)).getOrThrow()
            val local = h.localUntil("quick note entry shown offline") { list -> list.size == 1 }
            assertEquals(TransactionId(draft.recordIdFor(0)), local.single().id)
            assertEquals(lkr(880_000), h.balance(bank, local).current)
            h.online()
            h.flush()

            // Only the confirmed proposal exists; the expected payment is still just a draft proposal.
            assertEquals(listOf(draft.recordIdFor(0)), h.serverDocs(FirestoreCollections.TRANSACTIONS).map { it.id })
            assertEquals(0, h.serverDocs(FirestoreCollections.EXPECTED_PAYMENTS).size)
            assertEquals(lkr(120_000), spent(h, h.serverLedger()))

            // Confirming the expected proposal records an expectation, never money.
            h.confirmQuickNote(draft, 1, QuickNoteReviewRules.inputFrom(draft.proposals[1]), today, now).getOrThrow()
            h.flush()
            assertEquals(listOf(draft.recordIdFor(1)), h.serverDocs(FirestoreCollections.EXPECTED_PAYMENTS).map { it.id })
            val afterExpected = h.serverLedger()
            assertEquals(1, afterExpected.size)
            assertEquals(lkr(880_000), h.balance(bank, afterExpected).current)
            assertEquals(lkr(120_000), spent(h, afterExpected))
            assertEquals(lkr(0), h.summaryToday(afterExpected).received.amount)

            // Replaying everything after the fact still stores one of each.
            h.confirmQuickNote(draft, 0, actual, today, now.plusSeconds(60)).getOrThrow()
            h.confirmQuickNote(draft, 1, QuickNoteReviewRules.inputFrom(draft.proposals[1]), today, now.plusSeconds(60)).getOrThrow()
            h.flush()
            delay(500)
            assertEquals(1, h.serverDocs(FirestoreCollections.TRANSACTIONS).size)
            assertEquals(1, h.serverDocs(FirestoreCollections.EXPECTED_PAYMENTS).size)

            // Correct then undo the confirmed quick-note entry.
            val entry = h.serverLedger().single()
            h.correct(entry, TransactionChanges(amount = lkr(125_000)), CorrectionId("qn-${UUID.randomUUID()}"), now.plusSeconds(120)).getOrThrow()
            h.flush()
            assertEquals(lkr(875_000), h.balance(bank, h.serverLedger()).current)
            h.void(h.serverLedger().single(), now.plusSeconds(180)).getOrThrow()
            h.flush()
            val afterUndo = h.serverLedger()
            assertEquals(lkr(1_000_000), h.balance(bank, afterUndo).current)
            assertEquals(lkr(0), spent(h, afterUndo))
            assertEquals(1, h.serverDocs(FirestoreCollections.EXPECTED_PAYMENTS).size) // expectations are not undone by it
            assertSameLedger(h.localLedger(), afterUndo)
        } finally { h.close() }
    }

    @Test
    fun aTransferLegLinkedToAMessageIsNotDoubleCountedAndUndoingItVoidsBothLegsAndTheFee() = runBlocking {
        val h = IntegrationHarness.open("transfer-link")
        try {
            val bank = h.seedAccount("bank")
            val cash = h.seedAccount("cash", type = AccountType.CASH, openingMinor = 0)
            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            val transfer = ManualEntryRules.newTransfer(
                TransferId("tr-${UUID.randomUUID()}"),
                TransferDetails(from = bank, to = cash, amount = lkr(500_000), fee = lkr(3_000), date = ZarTime.today(), note = null),
                now,
            )
            h.offline()
            h.saveTransfer(transfer, bank, cash).getOrThrow()
            val local = h.localUntil("transfer shown offline") { list -> list.size == 3 }
            assertEquals(lkr(497_000), h.balance(bank, local).current)
            assertEquals(lkr(500_000), h.balance(cash, local).current)
            h.online()
            h.flush()

            // The bank message that reports the same outgoing leg links to it instead of posting a second expense.
            val capture = CaptureTextUseCase(BankSmsParser(), h.drafts)
            val drafted = capture(sms, DraftSource.SHARED_TEXT, now).getOrThrow() as CaptureOutcome.Drafted
            val draft = step("read draft") { h.drafts.observeDrafts().first { list -> list.any { it.id == drafted.draftId } } }.single { it.id == drafted.draftId }
            LinkCapturedDraftUseCase(h.drafts, h.ledger)(draft, transfer.outLegId, now.plusSeconds(10)).getOrThrow()
            h.flush()

            val server = h.serverLedger()
            assertEquals(setOf(transfer.outLegId.value, transfer.inLegId.value, transfer.fee!!.id.value), server.map { it.id.value }.toSet())
            assertEquals(1, h.serverDocs(FirestoreCollections.EVIDENCE_LINKS).size)
            assertEquals(lkr(497_000), h.balance(bank, server).current)
            assertEquals(lkr(500_000), h.balance(cash, server).current)
            // A transfer is neither income nor spending; only its fee is spending.
            assertEquals(lkr(3_000), spent(h, server))
            assertEquals(lkr(0), h.summaryToday(server).received.amount)

            // Undoing one leg voids both legs and the fee together; balances return to opening.
            h.void(server.single { it.id == transfer.outLegId }, now.plusSeconds(60)).getOrThrow()
            h.flush()
            val afterUndo = h.serverLedger()
            assertTrue(afterUndo.all { it.isVoided })
            assertEquals(3, h.serverDocs(FirestoreCollections.VOIDS).size)
            assertEquals(lkr(1_000_000), h.balance(bank, afterUndo).current)
            assertEquals(lkr(0), h.balance(cash, afterUndo).current)
            assertEquals(lkr(0), spent(h, afterUndo))
            assertSameLedger(h.localLedger(), afterUndo)
            assertFalse(h.localLedger().active().any())
        } finally { h.close() }
    }
}
