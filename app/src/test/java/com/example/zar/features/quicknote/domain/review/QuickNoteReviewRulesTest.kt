package com.example.zar.features.quicknote.domain.review

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickNoteReviewRulesTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val yesterday = today.minusDays(1)
    private val now = T0.plusSeconds(1_000)
    private val accounts = listOf(bank, cash, payoneer)

    private fun draft(vararg proposals: DraftProposal) =
        Draft(DraftId("d1"), DraftSource.QUICK_NOTE, proposals.toList(), DraftStatus.PENDING_REVIEW, T0)

    private fun expense(account: String? = "cash", amount: String = "1250", date: LocalDate? = yesterday) = ProposalInput.Actual(
        type = TransactionType.EXPENSE, amount = amount, currency = null, accountId = account?.let { com.example.zar.core.domain.model.AccountId(it) },
        date = date, categoryId = "groceries", spendingClass = SpendingClass.DISCRETIONARY, title = "Keells Super", note = "", refundOf = null, uncertain = emptySet(),
    )

    private fun check(input: ProposalInput, entries: List<com.example.zar.core.domain.model.LedgerEntry> = emptyList(), index: Int = 0) =
        QuickNoteReviewRules.check(draft(), index, input, accounts, entries, today, now)

    // --- Reading the draft ---------------------------------------------------------------------

    @Test
    fun theInputKeepsWhatWasGuessedAndWhatIsMissing() {
        val proposal = DraftProposal.ActualTransaction(
            type = TransactionType.EXPENSE, amount = lkr(1_250), accountId = null, date = yesterday,
            merchant = "Keells Super", uncertainFields = setOf(DraftField.AMOUNT),
        )
        val input = QuickNoteReviewRules.inputFrom(proposal) as ProposalInput.Actual
        assertEquals("1250", input.amount)
        assertNull("No account is picked for the person", input.accountId)
        assertEquals(setOf(DraftField.AMOUNT), input.uncertain)
        assertEquals("Keells Super", input.title)
        assertNull(input.refundOf)
    }

    @Test
    fun aMissingAmountStaysBlankInsteadOfBecomingZero() {
        val input = QuickNoteReviewRules.inputFrom(DraftProposal.ActualTransaction(TransactionType.EXPENSE, null, null, null)) as ProposalInput.Actual
        assertEquals("", input.amount)
        assertNull(input.currency)
    }

    // --- Actual records ------------------------------------------------------------------------

    @Test
    fun aCompleteExpenseBecomesAQuickNoteRecordWithAStableId() {
        val result = check(expense())
        assertTrue(result.blockers.isEmpty())
        val record = result.record as ConfirmableRecord.Ledger
        assertEquals(TransactionId("draft-d1-0"), record.transaction.id)
        assertEquals(RecordSource.QUICK_NOTE, record.transaction.source)
        assertEquals(lkr(1_250), record.transaction.amount)
        assertEquals("groceries", record.transaction.categoryId)
        assertEquals(record.transaction.id, (check(expense()).record as ConfirmableRecord.Ledger).transaction.id)
    }

    @Test
    fun eachProposalOfADraftGetsItsOwnId() {
        val first = (check(expense(), index = 0).record as ConfirmableRecord.Ledger).transaction.id
        val second = (check(expense(), index = 1).record as ConfirmableRecord.Ledger).transaction.id
        assertEquals("draft-d1-0", first.value)
        assertEquals("draft-d1-1", second.value)
    }

    @Test
    fun aMissingAccountBlocksTheSaveAndIsNeverFilledIn() {
        val result = check(expense(account = null))
        assertEquals(setOf(ProposalBlocker.ACCOUNT_MISSING), result.blockers)
        assertNull(result.record)
    }

    @Test
    fun aMissingAmountOrDateBlocksTheSave() {
        assertEquals(setOf(ProposalBlocker.AMOUNT_MISSING), check(expense(amount = "")).blockers)
        assertEquals(setOf(ProposalBlocker.AMOUNT_INVALID), check(expense(amount = "abc")).blockers)
        assertEquals(setOf(ProposalBlocker.AMOUNT_INVALID), check(expense(amount = "0")).blockers)
        assertEquals(setOf(ProposalBlocker.DATE_MISSING), check(expense(date = null)).blockers)
    }

    @Test
    fun aPaymentThatAlreadyHappenedCannotBeInTheFuture() {
        assertEquals(setOf(ProposalBlocker.DATE_IN_FUTURE), check(expense(date = today.plusDays(1))).blockers)
        assertTrue(check(expense(date = today)).blockers.isEmpty())
    }

    @Test
    fun anAccountInAnotherCurrencyNeverReinterpretsTheNumber() {
        val statedInDollars = expense(account = "payoneer").copy(currency = usd(1).currency)
        assertTrue(check(statedInDollars).blockers.isEmpty())
        val forcedOntoRupees = expense(account = "cash").copy(currency = usd(1).currency)
        assertTrue(ProposalBlocker.CURRENCY_MISMATCH in check(forcedOntoRupees).blockers)
    }

    @Test
    fun aMissingTypeBlocksTheSave() {
        assertTrue(ProposalBlocker.TYPE_MISSING in check(expense().copy(type = null)).blockers)
    }

    @Test
    fun incomeCarriesNoSpendingClassAndNoCategory() {
        val record = check(expense().copy(type = TransactionType.INCOME, title = "Salary")).record as ConfirmableRecord.Ledger
        assertEquals(TransactionType.INCOME, record.transaction.type)
        assertNull(record.transaction.spendingClass)
        assertNull(record.transaction.categoryId)
    }

    @Test
    fun anUnknownCategoryIsDroppedAndAnExpenseWithoutAClassIsUnknown() {
        val record = check(expense().copy(categoryId = "made-up", spendingClass = null)).record as ConfirmableRecord.Ledger
        assertNull(record.transaction.categoryId)
        assertEquals(SpendingClass.UNKNOWN, record.transaction.spendingClass)
    }

    @Test
    fun overlongTextIsCutToTheLimitsFirestoreEnforces() {
        val record = check(expense().copy(title = "x".repeat(300), note = "y".repeat(900))).record as ConfirmableRecord.Ledger
        assertEquals(120, record.transaction.merchant?.length)
        assertEquals(500, record.transaction.note?.length)
    }

    // --- Refunds -------------------------------------------------------------------------------

    private val purchase = entry(tx("p1", TransactionType.EXPENSE, lkr(2_300), account = bank))
    private fun refund(purchaseId: String? = "p1", amount: String = "500") =
        expense(account = null, amount = amount).copy(type = TransactionType.REFUND, refundOf = purchaseId?.let(::TransactionId))

    @Test
    fun aRefundCannotBeSavedUntilThePurchaseIsPicked() {
        val result = check(refund(purchaseId = null), listOf(purchase))
        assertTrue(ProposalBlocker.PURCHASE_MISSING in result.blockers)
        assertNull(result.record)
    }

    @Test
    fun aRefundGoesBackToThePurchasesOwnAccount() {
        val record = check(refund(), listOf(purchase)).record as ConfirmableRecord.Ledger
        assertEquals(TransactionType.REFUND, record.transaction.type)
        assertEquals(bank.id, record.transaction.accountId)
        assertEquals(TransactionId("p1"), record.transaction.refundOf)
        assertEquals(RecordSource.QUICK_NOTE, record.transaction.source)
    }

    @Test
    fun aRefundCannotBeMoreThanWhatIsLeftOfThePurchase() {
        val result = check(refund(amount = "2301"), listOf(purchase))
        assertTrue(ProposalBlocker.REFUND_TOO_HIGH in result.blockers)
        assertNull(result.record)
    }

    @Test
    fun aVoidedPurchaseCannotTakeARefund() {
        val voided = entry(tx("p1", TransactionType.EXPENSE, lkr(2_300), account = bank), voided = true)
        assertTrue(ProposalBlocker.PURCHASE_MISSING in check(refund(), listOf(voided)).blockers)
    }

    @Test
    fun theRefundPickerListsOnlyPurchasesWithSomethingLeft() {
        val refunded = entry(tx("p2", TransactionType.EXPENSE, lkr(100), account = bank))
        val fullRefund = entry(tx("r2", TransactionType.REFUND, lkr(100), account = bank, refundOf = "p2"))
        val voided = entry(tx("p3", TransactionType.EXPENSE, lkr(50), account = bank), voided = true)
        val income = entry(tx("i1", TransactionType.INCOME, lkr(5_000), account = bank))
        val onCash = entry(tx("p4", TransactionType.EXPENSE, lkr(70), account = cash))
        val all = listOf(purchase, refunded, fullRefund, voided, income, onCash)

        assertEquals(setOf("p1", "p4"), QuickNoteReviewRules.refundCandidates(all, null).map { it.id.value }.toSet())
        assertEquals(listOf("p1"), QuickNoteReviewRules.refundCandidates(all, bank.id).map { it.id.value })
    }

    // --- Expected payments ---------------------------------------------------------------------

    private fun expected(direction: Direction? = Direction.OUT, title: String = "Electricity bill", due: LocalDate? = today.plusDays(5)) =
        ProposalInput.Expected(direction, "4500", null, due, title, null, emptySet())

    @Test
    fun anExpectedPaymentIsNeverASavedTransaction() {
        val record = check(expected()).record
        assertTrue(record is ConfirmableRecord.Expectation)
        val payment = (record as ConfirmableRecord.Expectation).payment
        assertEquals(ExpectedSource.QuickNote, payment.source)
        assertEquals(Direction.OUT, payment.direction)
        assertEquals("draft-d1-0", payment.id.value)
        assertNull("No account is invented for something merely expected", payment.accountId)
    }

    @Test
    fun anExpectedPaymentMayBeDueInTheFuture() {
        assertTrue(check(expected(due = today.plusDays(30))).blockers.isEmpty())
    }

    @Test
    fun anExpectedPaymentNeedsADirectionADateAndATitle() {
        assertEquals(setOf(ProposalBlocker.DIRECTION_MISSING), check(expected(direction = null)).blockers)
        assertEquals(setOf(ProposalBlocker.DATE_MISSING), check(expected(due = null)).blockers)
        assertEquals(setOf(ProposalBlocker.TITLE_MISSING), check(expected(title = "  ")).blockers)
    }

    // --- Transfers -----------------------------------------------------------------------------

    private fun transfer(from: String? = "bank", to: String? = "cash", amount: String = "3000", received: String = "") =
        ProposalInput.Transfer(
            from?.let { com.example.zar.core.domain.model.AccountId(it) }, to?.let { com.example.zar.core.domain.model.AccountId(it) },
            amount, null, received, yesterday, emptySet(),
        )

    @Test
    fun aTransferBetweenAccountsOfOneCurrencyMovesTheSameAmount() {
        val record = check(transfer()).record as ConfirmableRecord.Moved
        assertEquals(lkr(3_000), record.transfer.sent)
        assertEquals(lkr(3_000), record.transfer.received)
        assertEquals("draft-d1-0", record.transfer.id.value)
        assertEquals(RecordSource.QUICK_NOTE, record.transfer.source)
    }

    @Test
    fun aTransferNeedsTwoDifferentAccounts() {
        assertEquals(setOf(ProposalBlocker.FROM_MISSING), check(transfer(from = null)).blockers)
        assertEquals(setOf(ProposalBlocker.TO_MISSING), check(transfer(to = null)).blockers)
        assertEquals(setOf(ProposalBlocker.SAME_ACCOUNT), check(transfer(to = "bank")).blockers)
    }

    @Test
    fun aTransferAcrossCurrenciesNeedsTheReceivedAmountToBeStated() {
        val blocked = check(transfer(to = "payoneer"))
        assertEquals(setOf(ProposalBlocker.RECEIVED_MISSING), blocked.blockers)
        val record = check(transfer(to = "payoneer", received = "10")).record as ConfirmableRecord.Moved
        assertEquals(usd(10), record.transfer.received)
        assertFalse("The rate is left missing, never guessed", record.transfer.receivedConversion == record.transfer.sentConversion)
    }

    @Test
    fun aStatedReceivedCurrencyIsKeptAndMustMatchTheDestination() {
        val input = QuickNoteReviewRules.inputFrom(
            DraftProposal.TransferBetweenAccounts(bank.id, cash.id, lkr(3000), usd(10), yesterday),
        ) as ProposalInput.Transfer
        assertEquals(usd(1).currency, input.receivedCurrency)
        assertTrue(ProposalBlocker.CURRENCY_MISMATCH in check(input).blockers)
        assertNull(check(input).record)
        assertTrue(check(input.copy(toId = payoneer.id)).blockers.isEmpty())
    }

    @Test
    fun aSameCurrencyTransferNeverSilentlyReplacesAStatedReceivedAmount() {
        val input = transfer(received = "2900")
        assertEquals(setOf(ProposalBlocker.RECEIVED_MISMATCH), check(input).blockers)
        assertNull(check(input).record)
        assertTrue(check(input.copy(received = "3000")).blockers.isEmpty())
        assertTrue(ProposalBlocker.RECEIVED_MISSING in check(input.copy(received = "invalid")).blockers)
    }
}
