package com.example.zar.features.home

import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.freelance.FreelanceFixtures
import com.example.zar.features.freelance.FreelanceFixtures.TODAY
import com.example.zar.features.freelance.FreelanceFixtures.match
import com.example.zar.features.freelance.FreelanceFixtures.milestone
import com.example.zar.features.freelance.FreelanceFixtures.project
import com.example.zar.features.home.domain.model.HomeCommitments
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCommitmentsTest {

    private val rig = HomeRig()

    private fun setLedger(
        vararg transactions: Transaction,
        corrections: List<Correction> = emptyList(),
        voids: List<VoidMarker> = emptyList(),
    ) {
        rig.ledger.entries.value = LedgerProjector.project(transactions.toList(), corrections, voids)
    }

    private fun bill(id: String, firstDue: LocalDate, status: BillStatus = BillStatus.ACTIVE) = Bill(
        BillId(id), "Bill $id", TestLedger.bank.id, lkr(5_850), BillFrequency.MONTHLY, firstDue, status, TestLedger.T0,
    )

    private suspend fun commitments(): HomeCommitments = rig.commitments(TODAY).first()

    @Test
    fun owedAmountIsWhatMilestonesStillExpect() = runTest {
        rig.projects.projects.value = listOf(project("p1"))
        rig.expected.payments.value = listOf(milestone("m1", "p1", usd(1_000)), milestone("m2", "p1", usd(500)))

        assertEquals(listOf(usd(1_500)), commitments().outstanding.owed)
    }

    @Test
    fun partialReceiptsReduceWhatIsOwed() = runTest {
        rig.projects.projects.value = listOf(project("p1"))
        rig.expected.payments.value = listOf(milestone("m1", "p1", usd(1_000)))
        setLedger(tx("r1", TransactionType.INCOME, usd(400), account = TestLedger.payoneer))
        rig.expected.matches.value = listOf(match("m1", "r1", usd(400)))

        assertEquals(listOf(usd(600)), commitments().outstanding.owed)
    }

    @Test
    fun correctingAReceiptUpdatesWhatIsOwed() = runTest {
        rig.projects.projects.value = listOf(project("p1"))
        rig.expected.payments.value = listOf(milestone("m1", "p1", usd(1_000)))
        rig.expected.matches.value = listOf(match("m1", "r1", usd(400)))
        setLedger(
            tx("r1", TransactionType.INCOME, usd(400), account = TestLedger.payoneer),
            corrections = listOf(TestLedger.correction("fix", "r1", TransactionChanges(amount = usd(300)))),
        )

        assertEquals(listOf(usd(700)), commitments().outstanding.owed)
    }

    @Test
    fun undoingAReceiptReopensTheBalance() = runTest {
        rig.projects.projects.value = listOf(project("p1"))
        rig.expected.payments.value = listOf(milestone("m1", "p1", usd(1_000)))
        rig.expected.matches.value = listOf(match("m1", "r1", usd(400)))
        setLedger(
            tx("r1", TransactionType.INCOME, usd(400), account = TestLedger.payoneer),
            voids = listOf(TestLedger.void("r1")),
        )

        assertEquals(listOf(usd(1_000)), commitments().outstanding.owed)
    }

    @Test
    fun overdueMilestonesAreShownApartFromTheTotal() = runTest {
        rig.projects.projects.value = listOf(project("p1"))
        rig.expected.payments.value = listOf(
            milestone("late", "p1", usd(200), due = TODAY.minusDays(3)),
            milestone("soon", "p1", usd(800), due = TODAY.plusDays(10)),
        )

        val outstanding = commitments().outstanding
        assertEquals(listOf(usd(1_000)), outstanding.owed)
        assertEquals(listOf(usd(200)), outstanding.overdue)
    }

    @Test
    fun currenciesAreNeverMixedOrConverted() = runTest {
        rig.projects.projects.value = listOf(project("usd", Currencies.USD), project("lkr", Currencies.LKR))
        rig.expected.payments.value = listOf(milestone("m1", "usd", usd(100)), milestone("m2", "lkr", lkr(40_000)))

        assertEquals(listOf(lkr(40_000), usd(100)), commitments().outstanding.owed)
    }

    @Test
    fun nothingOwedIsEmptyNotZero() = runTest {
        assertTrue(commitments().outstanding.isEmpty)
    }

    @Test
    fun expectedClientMoneyNeverEntersReceived() = runTest {
        rig.projects.projects.value = listOf(project("p1"))
        rig.expected.payments.value = listOf(milestone("m1", "p1", usd(5_000)))

        val overview = rig.overview(YearMonth.from(TODAY)).first()
        assertTrue(overview.summary.received.amount.isZero)
        assertEquals(listOf(usd(5_000)), commitments().outstanding.owed)
    }

    @Test
    fun nextBillIsTheSoonestDueDateWithItsExpectedAmount() = runTest {
        rig.bills.bills.value = listOf(bill("later", TODAY.plusDays(20)), bill("soon", TODAY.plusDays(5)))

        val next = commitments().nextBill!!
        assertEquals(BillId("soon"), next.billId)
        assertEquals("Bill soon", next.name)
        assertEquals(TODAY.plusDays(5), next.dueDate)
        assertEquals(lkr(5_850), next.amount)
    }

    @Test
    fun aBillWithNoLinkedPaymentIsStillJustExpected() = runTest {
        // Nothing links a payment, yet the card reports the date and expected amount only.
        rig.bills.bills.value = listOf(bill("b1", TODAY.plusDays(2)))
        setLedger()

        val next = commitments().nextBill!!
        assertEquals(lkr(5_850), next.amount)
        assertEquals(TODAY.plusDays(2), next.dueDate)
    }

    @Test
    fun stoppedBillsAndNoBillsHaveNoNextBill() = runTest {
        assertNull(commitments().nextBill)
        rig.bills.bills.value = listOf(bill("old", TODAY.plusDays(2), status = BillStatus.STOPPED))
        assertNull(commitments().nextBill)
    }

    @Test
    fun newReceiptsAndBillsAreReflectedLive() = runTest {
        rig.projects.projects.value = listOf(project("p1"))
        rig.expected.payments.value = listOf(milestone("m1", "p1", usd(1_000)))
        assertEquals(listOf(usd(1_000)), commitments().outstanding.owed)

        setLedger(tx("r1", TransactionType.INCOME, usd(1_000), account = TestLedger.payoneer))
        rig.expected.matches.value = listOf(FreelanceFixtures.match("m1", "r1", usd(1_000)))

        assertTrue(commitments().outstanding.isEmpty)
    }
}
