package com.example.zar.features.export

import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedPaymentStatus
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.features.export.domain.ExpectedPaymentsCsv
import com.example.zar.features.freelance.FreelanceFixtures
import com.example.zar.features.freelance.FreelanceFixtures.TODAY
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpectedPaymentsCsvTest {

    private val accounts = listOf(TestLedger.bank, TestLedger.payoneer)

    private fun records(
        payments: List<ExpectedPayment>,
        matches: List<PaymentMatch> = emptyList(),
        transactions: List<Transaction> = emptyList(),
        voids: List<VoidMarker> = emptyList(),
    ) = csvRecords(
        ExpectedPaymentsCsv.build(
            payments, matches, LedgerProjector.project(transactions, emptyList(), voids), accounts, TODAY,
        ).text,
    )

    private fun milestone(id: String, amount: com.example.zar.core.domain.model.money.Money, due: LocalDate = TODAY.plusDays(10)) =
        FreelanceFixtures.milestone(id, "p1", amount, due)

    private fun bill(id: String, billId: String, due: LocalDate, amount: Long = 2_450) = ExpectedPayment(
        id = ExpectedPaymentId(id),
        direction = Direction.OUT,
        amount = lkr(amount),
        dueDate = due,
        title = "Dialog",
        source = ExpectedSource.BillOccurrence(billId, due),
        accountId = TestLedger.bank.id,
        createdAt = TestLedger.T0,
    )

    @Test
    fun theHeaderCarriesIdsCurrencySourceAndSettlement() {
        val header = parseCsv(ExpectedPaymentsCsv.build(emptyList(), emptyList(), emptyList(), accounts, TODAY).text).single()
        assertEquals(ExpectedPaymentsCsv.HEADER, header)
        listOf("expected_payment_id", "currency", "source_type", "settlement_state", "matched_amount", "due_date")
            .forEach { assertTrue("missing $it", it in header) }
    }

    @Test
    fun anUnmatchedMilestoneIsNotMatchedAndFullyRemaining() {
        val row = records(listOf(milestone("m1", usd(1_000)))).single()
        assertEquals("m1", row["expected_payment_id"])
        assertEquals("OPEN", row["status"])
        assertEquals("NOT_MATCHED", row["settlement_state"])
        assertEquals("IN", row["direction"])
        assertEquals("1000.00", row["amount"])
        assertEquals("USD", row["currency"])
        assertEquals("0.00", row["matched_amount"])
        assertEquals("1000.00", row["remaining_amount"])
        assertEquals("FREELANCE_MILESTONE", row["source_type"])
        assertEquals("p1", row["project_id"])
        assertEquals("m1", row["milestone_id"])
        assertEquals("", row["linked_transaction_ids"])
    }

    @Test
    fun aPartialReceiptShowsMatchedRemainingAndTheLinkedTransaction() {
        val row = records(
            payments = listOf(milestone("m1", usd(1_000))),
            matches = listOf(FreelanceFixtures.match("m1", "r1", usd(400))),
            transactions = listOf(tx("r1", TransactionType.INCOME, usd(400), account = TestLedger.payoneer)),
        ).single()
        assertEquals("PARTIALLY_MATCHED", row["settlement_state"])
        assertEquals("400.00", row["matched_amount"])
        assertEquals("600.00", row["remaining_amount"])
        assertEquals("r1", row["linked_transaction_ids"])
    }

    @Test
    fun aFullyMatchedMilestoneIsMatched() {
        val row = records(
            payments = listOf(milestone("m1", usd(500))),
            matches = listOf(FreelanceFixtures.match("m1", "r1", usd(500))),
            transactions = listOf(tx("r1", TransactionType.INCOME, usd(500), account = TestLedger.payoneer)),
        ).single()
        assertEquals("MATCHED", row["settlement_state"])
        assertEquals("0.00", row["remaining_amount"])
    }

    @Test
    fun anUndoneReceiptNoLongerCountsOrLinks() {
        val row = records(
            payments = listOf(milestone("m1", usd(500))),
            matches = listOf(FreelanceFixtures.match("m1", "r1", usd(500))),
            transactions = listOf(tx("r1", TransactionType.INCOME, usd(500), account = TestLedger.payoneer)),
            voids = listOf(TestLedger.void("r1")),
        ).single()
        assertEquals("NOT_MATCHED", row["settlement_state"])
        assertEquals("0.00", row["matched_amount"])
        assertEquals("", row["linked_transaction_ids"])
    }

    @Test
    fun oneReceiptIsNeverCountedTwice() {
        val rows = records(
            payments = listOf(milestone("m1", usd(500)), milestone("m2", usd(500))),
            matches = listOf(
                FreelanceFixtures.match("m1", "r1", usd(500), offset = 1),
                FreelanceFixtures.match("m2", "r1", usd(500), offset = 2),
            ),
            transactions = listOf(tx("r1", TransactionType.INCOME, usd(500), account = TestLedger.payoneer)),
        ).associateBy { it["expected_payment_id"] }
        assertEquals("500.00", rows.getValue("m1")["matched_amount"])
        assertEquals("0.00", rows.getValue("m2")["matched_amount"])
    }

    @Test
    fun anOverdueMilestoneShowsWhatIsOverdue() {
        val row = records(listOf(milestone("late", usd(300), due = TODAY.minusDays(5)))).single()
        assertEquals("300.00", row["overdue_amount"])
        assertEquals("NOT_MATCHED", row["settlement_state"])
    }

    @Test
    fun aCancelledMilestoneIsCancelledAndNeverOverdue() {
        val cancelled = FreelanceFixtures.milestone("gone", "p1", usd(300), TODAY.minusDays(5), ExpectedPaymentStatus.CANCELLED)
        val row = records(listOf(cancelled)).single()
        assertEquals("CANCELLED", row["status"])
        assertEquals("CANCELLED", row["settlement_state"])
        assertEquals("0.00", row["overdue_amount"])
    }

    @Test
    fun aBillDueDateIsOutgoingWithItsBillAndAccount() {
        val row = records(
            payments = listOf(bill("bill-dialog-1", "dialog", TODAY.plusDays(3))),
            matches = listOf(PaymentMatch(ExpectedPaymentId("bill-dialog-1"), TransactionId("pay-1"), lkr(2_450), TestLedger.T0)),
            transactions = listOf(tx("pay-1", TransactionType.EXPENSE, lkr(2_450))),
        ).single()
        assertEquals("OUT", row["direction"])
        assertEquals("BILL_OCCURRENCE", row["source_type"])
        assertEquals("dialog", row["bill_id"])
        assertEquals(TODAY.plusDays(3).toString(), row["occurrence_date"])
        assertEquals("bank", row["account_id"])
        assertEquals("Bank", row["account_name"])
        assertEquals("MATCHED", row["settlement_state"])
        assertEquals("pay-1", row["linked_transaction_ids"])
    }

    @Test
    fun quickNoteAndManualSourcesAreNamed() {
        val quick = milestone("q", lkr(100)).copy(source = ExpectedSource.QuickNote)
        val manual = milestone("man", lkr(100)).copy(source = ExpectedSource.Manual)
        val sources = records(listOf(quick, manual)).associate { it["expected_payment_id"] to it["source_type"] }
        assertEquals("QUICK_NOTE", sources["q"])
        assertEquals("MANUAL", sources["man"])
    }

    @Test
    fun rowsAreOrderedByDueDate() {
        val rows = records(
            listOf(
                milestone("later", usd(1), due = TODAY.plusDays(20)),
                milestone("sooner", usd(1), due = TODAY.plusDays(2)),
            ),
        )
        assertEquals(listOf("sooner", "later"), rows.map { it["expected_payment_id"] })
    }

    @Test
    fun theTitleIsEscapedAndNeutralised() {
        val tricky = milestone("m1", usd(1)).copy(title = "=Design, \"phase 2\"")
        val row = records(listOf(tricky)).single()
        assertEquals("'=Design, \"phase 2\"", row["title"])
    }
}
