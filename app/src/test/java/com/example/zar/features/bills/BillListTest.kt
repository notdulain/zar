package com.example.zar.features.bills

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.bills.domain.BillSchedule
import com.example.zar.features.bills.domain.BillsCalculator
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillCancellation
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.bills.presentation.model.BillList
import com.example.zar.features.bills.presentation.model.NeedsLook
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillListTest {

    // A Wednesday, so this week ends on Sunday 25 Oct.
    private val today = LocalDate.of(2026, 10, 21)

    private fun bill(
        id: String,
        firstDue: LocalDate,
        frequency: BillFrequency = BillFrequency.MONTHLY,
        amount: Money = lkr(1_000),
        status: BillStatus = BillStatus.ACTIVE,
        cancellation: BillCancellation? = null,
    ) = Bill(BillId(id), id, bank.id, amount, frequency, firstDue, status, T0, cancellation = cancellation)

    private fun list(vararg bills: Bill, expected: List<com.example.zar.core.domain.model.ExpectedPayment> = emptyList(),
                     matches: List<PaymentMatch> = emptyList(), entries: List<com.example.zar.core.domain.model.LedgerEntry> = emptyList()) =
        BillList.from(BillsCalculator.overview(bills.toList(), listOf(bank), expected, matches, entries, today), today)

    @Test
    fun dueDatesSplitIntoThisWeekAndLater() {
        val list = list(
            bill("internet", LocalDate.of(2026, 9, 24), amount = lkr(5_850)),
            bill("netflix", LocalDate.of(2026, 9, 25), amount = lkr(2_190)),
            bill("rent", LocalDate.of(2026, 9, 1), amount = lkr(30_000)),
        )
        assertEquals(LocalDate.of(2026, 10, 25), list.weekEnd)
        assertEquals(listOf("internet", "netflix"), list.thisWeek.map { it.summary.bill.name })
        assertEquals(listOf(lkr(8_040)), list.thisWeekTotal)
        assertEquals(listOf("rent"), list.later.map { it.summary.bill.name })
        assertEquals(LocalDate.of(2026, 11, 1), list.later.single().occurrence.dueDate)
    }

    @Test
    fun missedDueDatesNeedALookOncePerBill() {
        // Weekly from 30 Sep: 30 Sep, 7 Oct and 14 Oct have passed with nothing linked.
        val list = list(bill("gym", LocalDate.of(2026, 9, 30), frequency = BillFrequency.WEEKLY))
        val missed = list.needsLook.single() as NeedsLook.Missed
        assertEquals(3, missed.count)
        assertEquals(LocalDate.of(2026, 10, 14), missed.latest.dueDate)
    }

    @Test
    fun matchedDueDatesShowAsPaidThisMonth() {
        val b = bill("gym", LocalDate.of(2026, 10, 3))
        val due = BillSchedule.expectedPayment(b, LocalDate.of(2026, 10, 3))
        val paid = tx("p", TransactionType.EXPENSE, lkr(1_000), date = LocalDate.of(2026, 10, 2))
        val list = list(b, expected = listOf(due), matches = listOf(PaymentMatch(due.id, paid.id, lkr(1_000), T0)), entries = listOf(entry(paid)))
        assertEquals(LocalDate.of(2026, 10, 2), list.paidThisMonth.single().paidOn)
        assertTrue("A paid due date doesn't need a look", list.needsLook.isEmpty())
    }

    @Test
    fun stoppedAndCancelledBillsAreEndedAndNotExpected() {
        val list = list(
            bill("stopped", LocalDate.of(2026, 10, 24), status = BillStatus.STOPPED),
            bill("cancelled", LocalDate.of(2026, 10, 24), cancellation = BillCancellation.Confirmed(LocalDate.of(2026, 10, 20))),
            // Cancelled from a later date: the due date before it is still expected.
            bill("ending", LocalDate.of(2026, 10, 24), cancellation = BillCancellation.Confirmed(LocalDate.of(2026, 11, 15))),
        )
        assertEquals(setOf("stopped", "cancelled"), list.ended.map { it.bill.name }.toSet())
        assertEquals(listOf("ending"), list.thisWeek.map { it.summary.bill.name })
        assertTrue(list.hasBills)
    }

    @Test
    fun noBills() {
        val list = list()
        assertFalse(list.hasBills)
        assertTrue(list.thisWeek.isEmpty() && list.later.isEmpty() && list.needsLook.isEmpty())
    }
}
