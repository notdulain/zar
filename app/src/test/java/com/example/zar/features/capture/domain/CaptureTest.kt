package com.example.zar.features.capture.domain

import com.example.zar.features.rules.rule
import com.example.zar.features.rules.FakeCategoryRules
import com.example.zar.core.data.remote.firestore.DraftDocumentMapper
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.repository.DraftRepository
import com.example.zar.core.domain.repository.LinkWrite
import com.example.zar.core.domain.model.Transaction
import java.time.Instant
import java.io.IOException
import com.example.zar.features.capture.domain.model.SmsIgnoreReason
import com.example.zar.features.capture.domain.model.SmsTransactionKind
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.example.zar.features.capture.domain.usecase.CaptureOutcome
import com.example.zar.features.capture.domain.usecase.CaptureTextUseCase
import com.example.zar.features.capture.domain.usecase.ConfirmCapturedDraftUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCaptureHealthUseCase
import com.example.zar.features.rules.domain.usecase.EditCategoryRuleUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTest {

    private val purchase = "Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111."
    private val credit = "LKR 240.00 credited to AC **3333 for Food\n12/10/2026 12:21:02\nTo Inq Call 0112303050\nGet protected -Do not Share OTP"

    private class FakeDrafts(val ledger: FakeLedgerRepository = FakeLedgerRepository()) : DraftRepository {
        var failConfirm = false
        val drafts = MutableStateFlow<Map<DraftId, Draft>>(emptyMap())
        override fun observePendingDrafts(): Flow<List<Draft>> = drafts.map { it.values.filter { d -> d.status == DraftStatus.PENDING_REVIEW } }
        override fun observeDrafts(): Flow<List<Draft>> = drafts.map { it.values.toList() }
        override suspend fun saveDraft(draft: Draft): Draft {
            val existing = drafts.value[draft.id]
            if (existing != null) return existing
            drafts.value = drafts.value + (draft.id to draft)
            return draft
        }
        override suspend fun confirmCapturedDraft(draft: Draft, transaction: Transaction) {
            if (failConfirm) throw IOException("Offline")
            val current = requireNotNull(drafts.value[draft.id])
            if (current.status == DraftStatus.CONFIRMED) return
            require(current.status == DraftStatus.PENDING_REVIEW)
            ledger.saveTransaction(transaction)
            drafts.value = drafts.value + (draft.id to current.copy(status = DraftStatus.CONFIRMED))
        }
        override suspend fun setStatus(id: DraftId, status: DraftStatus) {
            drafts.value[id]?.let { drafts.value = drafts.value + (id to it.copy(status = status)) }
        }
        override fun observeLinkWrite(id: DraftId, target: com.example.zar.core.domain.model.TransactionId): Flow<LinkWrite> = kotlinx.coroutines.flow.flowOf(LinkWrite.SAVED)
        override suspend fun closeAsLinked(draft: Draft, link: EvidenceLink): LinkWrite {
            val current = requireNotNull(drafts.value[draft.id])
            if (current.status == DraftStatus.PENDING_REVIEW) {
                ledger.linkEvidence(link)
                drafts.value = drafts.value + (draft.id to current.copy(status = DraftStatus.CONFIRMED))
            }
            return LinkWrite.SAVED
        }
    }

    @Test
    fun sameMessageGivesTheSameDraftIdWhateverTheSpacing() {
        assertEquals(CapturedDraftRules.draftIdFor(purchase), CapturedDraftRules.draftIdFor("  " + purchase.replace(" ", "\n ") + "\n"))
        assertTrue(CapturedDraftRules.draftIdFor(purchase) != CapturedDraftRules.draftIdFor(credit))
        assertTrue("The ID doesn't contain the text", "CAFE" !in CapturedDraftRules.draftIdFor(purchase).value)
    }

    @Test
    fun purchasesAreExpensesButCreditsAreNeverAssumedIncome() {
        assertEquals(TransactionType.EXPENSE to true, CapturedDraftRules.suggestedType(SmsTransactionKind.CARD_PURCHASE))
        assertEquals(null to false, CapturedDraftRules.suggestedType(SmsTransactionKind.ATM_WITHDRAWAL))
        assertEquals(null to false, CapturedDraftRules.suggestedType(SmsTransactionKind.ACCOUNT_CREDIT))
        assertEquals(null to false, CapturedDraftRules.suggestedType(SmsTransactionKind.REVERSAL))
    }

    @Test
    fun capturedTextBecomesAPendingDraftWithoutTheText() = runBlocking {
        val drafts = FakeDrafts()
        val outcome = CaptureTextUseCase(BankSmsParser(), drafts)(purchase, DraftSource.SHARED_TEXT, T0).getOrThrow()
        assertTrue(outcome is CaptureOutcome.Drafted)

        val draft = drafts.drafts.value.values.single()
        val proposal = draft.proposals.single() as DraftProposal.ActualTransaction
        assertEquals(DraftStatus.PENDING_REVIEW, draft.status)
        assertEquals(TransactionType.EXPENSE, proposal.type)
        assertEquals(lkr(800), proposal.amount)
        assertEquals(LocalDate.of(2026, 10, 11), proposal.date)
        assertNull("The user picks the account", proposal.accountId)
        assertEquals(setOf(DraftField.ACCOUNT), proposal.missingFields - setOf(DraftField.TYPE))
        assertTrue("Raw text is not kept", DraftDocumentMapper.toMap(draft).toString().contains("Dear Cardholder").not())
    }

    @Test
    fun sharingTheSameMessageTwiceKeepsOneDraft() = runBlocking {
        val drafts = FakeDrafts()
        val capture = CaptureTextUseCase(BankSmsParser(), drafts)
        capture(purchase, DraftSource.SHARED_TEXT, T0)
        capture(purchase, DraftSource.SHARED_TEXT, T0.plusSeconds(60))
        assertEquals(1, drafts.drafts.value.size)
    }

    @Test
    fun otpsDeclinesAndUnknownFormatsAreNotDrafted() = runBlocking {
        val drafts = FakeDrafts()
        val capture = CaptureTextUseCase(BankSmsParser(), drafts)
        assertEquals(CaptureOutcome.Ignored(SmsIgnoreReason.OTP), capture("Code 123456 is your One Time Password", DraftSource.SHARED_TEXT, T0).getOrThrow())
        assertEquals(CaptureOutcome.Unrecognised, capture("Your account was credited with LKR 5,000.00 today.", DraftSource.SHARED_TEXT, T0).getOrThrow())
        assertEquals(CaptureOutcome.Empty, capture("   ", DraftSource.SHARED_TEXT, T0).getOrThrow())
        assertTrue(drafts.drafts.value.isEmpty())
    }

    @Test
    fun confirmingSavesOneRecordAndClosesTheDraft() = runBlocking {
        val drafts = FakeDrafts()
        val ledger = drafts.ledger
        CaptureTextUseCase(BankSmsParser(), drafts)(credit, DraftSource.SHARED_TEXT, T0)
        val draft = drafts.drafts.value.values.single()
        val confirm = ConfirmCapturedDraftUseCase(drafts, FakeCategoryRules().observeUseCase())

        assertTrue(confirm(draft, bank, TransactionType.INCOME, null, Instant.parse("2026-10-22T04:00:00Z")).isSuccess)
        assertTrue(confirm(draft, bank, TransactionType.INCOME, null, Instant.parse("2026-10-22T04:00:00Z")).isSuccess)
        val saved = ledger.transactions.values.single()
        assertEquals(TransactionType.INCOME, saved.type)
        assertEquals(RecordSource.SHARED_TEXT, saved.source)
        assertEquals(DraftStatus.CONFIRMED, drafts.drafts.value.values.single().status)
    }

    @Test
    fun confirmRejectsTheWrongCurrencyAndUnsupportedTypes() = runBlocking {
        val drafts = FakeDrafts()
        val ledger = drafts.ledger
        CaptureTextUseCase(BankSmsParser(), drafts)(purchase, DraftSource.SHARED_TEXT, T0)
        val draft = drafts.drafts.value.values.single()
        val confirm = ConfirmCapturedDraftUseCase(drafts, FakeCategoryRules().observeUseCase())

        assertTrue(confirm(draft, payoneer, TransactionType.EXPENSE, SpendingClass.DISCRETIONARY, Instant.parse("2026-10-22T04:00:00Z")).exceptionOrNull() is LedgerError.CurrencyMismatch)
        assertTrue(confirm(draft, bank, TransactionType.TRANSFER_OUT, null, T0).isFailure)
        assertTrue(ledger.transactions.isEmpty())
        assertEquals(DraftStatus.PENDING_REVIEW, drafts.drafts.value.values.single().status)
    }

    @Test
    fun healthReportsTheLastCaptureAndWhatIsWaiting() = runBlocking {
        val drafts = FakeDrafts()
        val health = ObserveCaptureHealthUseCase(drafts)
        assertNull(health().first().lastCapturedAt)
        CaptureTextUseCase(BankSmsParser(), drafts)(purchase, DraftSource.SHARED_TEXT, T0)
        val after = health().first()
        assertEquals(T0, after.lastCapturedAt)
        assertEquals(1, after.waitingForReview)
    }

    @Test
    fun draftsRoundTripThroughFirestoreMaps() {
        val draft = CapturedDraftRules.draftFrom(
            CapturedDraftRules.draftIdFor(purchase),
            (BankSmsParser().parse(purchase) as com.example.zar.features.capture.domain.model.SmsParseResult.Parsed).sms,
            DraftSource.SHARED_TEXT,
            T0,
        )
        assertEquals(draft, DraftDocumentMapper.fromMap(DraftDocumentMapper.toMap(draft)))
        assertNull(DraftDocumentMapper.fromMap(mapOf("id" to "x")))
    }
    @Test fun replayDoesNotResetCaptureTimeOrReopenFinishedDraft() = runBlocking {
        val drafts = FakeDrafts()
        val capture = CaptureTextUseCase(BankSmsParser(), drafts)
        capture(purchase, DraftSource.SHARED_TEXT, T0).getOrThrow()
        val original = drafts.drafts.value.values.single()
        capture(purchase, DraftSource.SHARED_TEXT, T0.plusSeconds(60)).getOrThrow()
        assertEquals(original, drafts.drafts.value.values.single())
        drafts.setStatus(original.id, DraftStatus.DISCARDED)
        assertEquals(CaptureOutcome.AlreadyReviewed, capture(purchase, DraftSource.SHARED_TEXT, T0.plusSeconds(90)).getOrThrow())
        assertEquals(DraftStatus.DISCARDED, drafts.drafts.value.values.single().status)
    }

    @Test fun failedAtomicConfirmationKeepsDraftPendingAndRetrySavesOnce() = runBlocking {
        val drafts = FakeDrafts()
        CaptureTextUseCase(BankSmsParser(), drafts)(purchase, DraftSource.SHARED_TEXT, T0).getOrThrow()
        val draft = drafts.drafts.value.values.single()
        val confirm = ConfirmCapturedDraftUseCase(drafts, FakeCategoryRules().observeUseCase())
        val now = Instant.parse("2026-10-22T04:00:00Z")
        drafts.failConfirm = true
        assertTrue(confirm(draft, bank, TransactionType.EXPENSE, SpendingClass.UNKNOWN, now).isFailure)
        assertTrue(drafts.ledger.transactions.isEmpty())
        assertEquals(DraftStatus.PENDING_REVIEW, drafts.drafts.value.values.single().status)
        drafts.failConfirm = false
        confirm(draft, bank, TransactionType.EXPENSE, SpendingClass.UNKNOWN, now).getOrThrow()
        confirm(draft, bank, TransactionType.INCOME, null, now).getOrThrow()
        assertEquals(1, drafts.ledger.transactions.size)
        assertEquals(TransactionType.EXPENSE, drafts.ledger.transactions.values.single().type)
    }

    @Test fun futurePaymentsArchivedAccountsAndForeignDraftsCannotBeConfirmed() = runBlocking {
        val drafts = FakeDrafts()
        CaptureTextUseCase(BankSmsParser(), drafts)(purchase, DraftSource.SHARED_TEXT, T0).getOrThrow()
        val draft = drafts.drafts.value.values.single()
        val confirm = ConfirmCapturedDraftUseCase(drafts, FakeCategoryRules().observeUseCase())
        val now = Instant.parse("2026-10-22T04:00:00Z")
        assertTrue(confirm(draft, bank, TransactionType.EXPENSE, SpendingClass.UNKNOWN, T0).isFailure)
        assertTrue(confirm(draft, bank.copy(isArchived = true), TransactionType.EXPENSE, SpendingClass.UNKNOWN, now).isFailure)
        assertTrue(confirm(draft.copy(source = DraftSource.QUICK_NOTE), bank, TransactionType.EXPENSE, SpendingClass.UNKNOWN, now).isFailure)
        assertTrue(drafts.ledger.transactions.isEmpty())
    }
    // --- Category rules (SCRUM-39) ---

    private suspend fun confirmPurchaseWith(
        rules: List<com.example.zar.features.rules.domain.model.CategoryRule>,
        type: TransactionType = TransactionType.EXPENSE,
        message: String = purchase,
    ): FakeDrafts {
        val drafts = FakeDrafts()
        CaptureTextUseCase(BankSmsParser(), drafts)(message, DraftSource.SHARED_TEXT, T0).getOrThrow()
        val draft = drafts.drafts.value.values.single()
        val confirm = ConfirmCapturedDraftUseCase(drafts, FakeCategoryRules(rules).observeUseCase())
        confirm(draft, bank, type, if (type == TransactionType.EXPENSE) SpendingClass.UNKNOWN else null, Instant.parse("2026-10-22T04:00:00Z")).getOrThrow()
        return drafts
    }

    @Test fun aRememberedNameFilesTheCapturedExpenseAndKeepsTheAmount() = runBlocking {
        val rule = rule("CAFE SEVENTY THREE MALABE LK", category = "coffee-dining", id = "cafe", version = 2)
        val saved = confirmPurchaseWith(listOf(rule)).ledger.transactions.values.single()

        assertEquals("coffee-dining", saved.categoryId)
        assertEquals("cafe", saved.appliedRuleId)
        assertEquals(2, saved.appliedRuleVersion)
        assertEquals(lkr(800), saved.amount)
        assertEquals(SpendingClass.UNKNOWN, saved.spendingClass)
    }

    @Test fun anUnknownNameLeavesTheCapturedExpenseWithoutACategory() = runBlocking {
        val saved = confirmPurchaseWith(listOf(rule("Keells Super"))).ledger.transactions.values.single()

        assertNull(saved.categoryId)
        assertNull(saved.appliedRuleId)
    }

    @Test fun aSwitchedOffRuleAndDisagreeingRulesLeaveItUnresolved() = runBlocking {
        val off = rule("CAFE SEVENTY THREE MALABE LK", enabled = false)
        assertNull(confirmPurchaseWith(listOf(off)).ledger.transactions.values.single().categoryId)

        val a = rule("CAFE SEVENTY THREE MALABE LK", category = "groceries", id = "a")
        val b = rule("CAFE SEVENTY THREE MALABE LK", category = "coffee-dining", id = "b")
        assertNull(confirmPurchaseWith(listOf(a, b)).ledger.transactions.values.single().categoryId)
    }

    private fun purchaseAt(merchant: String, amount: String = "650.00") =
        "Dear Cardholder, Purchase at $merchant for LKR $amount on 11/10/26 10:49 AM has been authorised on your debit card ending #1111."

    @Test fun aGeneralNameLikePickMeIsNeverFiledEvenWhenARuleExistsForIt() = runBlocking {
        val general = rule("PickMe", category = "transport")
        val saved = confirmPurchaseWith(listOf(general), message = purchaseAt("PICKME")).ledger.transactions.values.single()

        assertNull(saved.categoryId)
        assertNull(saved.appliedRuleId)
        assertEquals(lkr(650), saved.amount)
    }

    @Test fun aPreciseNameOfTheSameBrandIsStillFiled() = runBlocking {
        val food = rule("PickMe Food", category = "coffee-dining")
        val saved = confirmPurchaseWith(listOf(food), message = purchaseAt("PICKME FOOD")).ledger.transactions.values.single()

        assertEquals("coffee-dining", saved.categoryId)
        assertEquals(food.id.value, saved.appliedRuleId)
    }

    @Test fun editingARuleOnlyChangesLaterCapturesNeverTheEntryAlreadySaved() = runBlocking {
        val drafts = FakeDrafts()
        val capture = CaptureTextUseCase(BankSmsParser(), drafts)
        val v1 = rule("CAFE SEVENTY THREE MALABE LK", category = "coffee-dining", id = "cafe", version = 1)
        val rules = FakeCategoryRules(listOf(v1))
        val confirm = ConfirmCapturedDraftUseCase(drafts, rules.observeUseCase())

        capture(purchase, DraftSource.SHARED_TEXT, T0).getOrThrow()
        confirm(drafts.drafts.value.values.single(), bank, TransactionType.EXPENSE, SpendingClass.UNKNOWN, Instant.parse("2026-10-22T04:00:00Z")).getOrThrow()
        val before = drafts.ledger.transactions.values.single()

        EditCategoryRuleUseCase(rules)(v1.id, "groceries", null, true, Instant.parse("2026-10-23T04:00:00Z")).getOrThrow()
        assertEquals(before, drafts.ledger.transactions.values.single())

        val later = purchase.replace("800.00", "900.00")
        capture(later, DraftSource.SHARED_TEXT, T0).getOrThrow()
        val second = drafts.drafts.value.values.single { it.status == DraftStatus.PENDING_REVIEW }
        confirm(second, bank, TransactionType.EXPENSE, SpendingClass.UNKNOWN, Instant.parse("2026-10-24T04:00:00Z")).getOrThrow()

        val saved = drafts.ledger.transactions.values.toList()
        assertEquals(2, saved.size)
        val first = saved.single { it.id == before.id }
        assertEquals("coffee-dining", first.categoryId)
        assertEquals(1, first.appliedRuleVersion)
        val newer = saved.single { it.id != before.id }
        assertEquals("groceries", newer.categoryId)
        assertEquals(2, newer.appliedRuleVersion)
    }

    @Test fun aRuleNeverCategorisesIncome() = runBlocking {
        val rule = rule("CAFE SEVENTY THREE MALABE LK", category = "coffee-dining")
        val saved = confirmPurchaseWith(listOf(rule), TransactionType.INCOME).ledger.transactions.values.single()

        assertNull(saved.categoryId)
        assertNull(saved.appliedRuleId)
        assertNull(saved.spendingClass)
    }

}
