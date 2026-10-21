package com.example.zar.features.bills

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.features.bills.data.repository.BillDocumentMapper
import com.example.zar.features.bills.domain.BillCheck
import com.example.zar.features.bills.domain.BillProblem
import com.example.zar.features.bills.domain.BillRules
import com.example.zar.features.bills.domain.BillSchedule
import com.example.zar.features.bills.domain.BillsCalculator
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.bills.domain.usecase.BillError
import com.example.zar.features.bills.domain.usecase.LinkBillPaymentUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillsTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun bill(
        id: String = "b1",
        frequency: BillFrequency = BillFrequency.MONTHLY,
        firstDue: LocalDate = LocalDate.of(2026, 10, 25),
        amount: com.example.zar.core.domain.model.money.Money = lkr(5_850),
        status: BillStatus = BillStatus.ACTIVE,
    ) = Bill(BillId(id), "Bill $id", bank.id, amount, frequency, firstDue, status, T0)

    // --- Schedule --------------------------------------------------------------------------

    @Test
    fun monthEndClampsButKeepsTheIntendedDay() {
        val b = bill(firstDue = LocalDate.of(2026, 1, 31))
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31"),
            (0..4).map { BillSchedule.dueDate(b, it).toString() },
        )
    }

    @Test
    fun leapDayYearlyBillReturnsToTheTwentyNinth() {
        val b = bill(frequency = BillFrequency.YEARLY, firstDue = LocalDate.of(2028, 2, 29))
        assertEquals(listOf("2028-02-29", "2029-02-28", "2032-02-29"), listOf(0, 1, 4).map { BillSchedule.dueDate(b, it).toString() })
    }

    @Test
    fun weeklyAndRangeAndNext() {
        val b = bill(frequency = BillFrequency.WEEKLY, firstDue = LocalDate.of(2026, 10, 5))
        assertEquals(listOf("2026-10-12", "2026-10-19"), BillSchedule.dueDates(b, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20)).map { it.toString() })
        assertEquals(LocalDate.of(2026, 10, 26), BillSchedule.nextDueDate(b, today))
    }

    @Test
    fun annualCostEstimates() {
        assertEquals(lkr(70_200), BillSchedule.annualCost(bill(frequency = BillFrequency.MONTHLY)))
        assertEquals(lkr(304_200), BillSchedule.annualCost(bill(frequency = BillFrequency.WEEKLY)))
        assertEquals(lkr(5_850), BillSchedule.annualCost(bill(frequency = BillFrequency.YEARLY)))
    }

    // --- Overview --------------------------------------------------------------------------

    @Test
    fun upcomingBillsAreExpectedOnlyAndNeverSpent() {
        val overview = BillsCalculator.overview(listOf(bill()), listOf(bank), emptyList(), emptyList(), emptyList(), today)
        val summary = overview.bills.single()

        assertEquals(LocalDate.of(2026, 10, 25), overview.next?.dueDate)
        assertEquals(lkr(5_850), overview.next?.amount)
        assertEquals(listOf(lkr(5_850)), overview.dueSoon)
        assertEquals(listOf(lkr(70_200)), overview.annual)
        assertEquals(MatchState.NOT_MATCHED, summary.next?.progress?.state)

        // Nothing is posted: Spent stays zero with no expenses recorded.
        val spent = LedgerSummaryCalculator.summarize(emptyList(), today.withDayOfMonth(1)..today).spent.total.amount
        assertEquals(lkr(0), spent)
    }

    @Test
    fun pastDueDatesWithoutAPaymentAreNotMatchedNotUnpaid() {
        val b = bill(firstDue = LocalDate.of(2026, 9, 10))
        val summary = BillsCalculator.overview(listOf(b), listOf(bank), emptyList(), emptyList(), emptyList(), today).bills.single()
        val past = summary.occurrences.first { it.dueDate == LocalDate.of(2026, 10, 10) }
        assertEquals(MatchState.NOT_MATCHED, past.progress.state)
        assertEquals(LocalDate.of(2026, 11, 10), summary.next?.dueDate)
        assertEquals(listOf("2026-11-10", "2026-10-10", "2026-09-10"), summary.occurrences.map { it.dueDate.toString() })
    }

    @Test
    fun linkedExpensesMatchAndUndoingThemReopensTheDueDate() {
        val b = bill(firstDue = LocalDate.of(2026, 10, 10))
        val due = BillSchedule.expectedPayment(b, LocalDate.of(2026, 10, 10))
        val expense = tx("e1", TransactionType.EXPENSE, lkr(5_850))
        val match = PaymentMatch(due.id, expense.id, lkr(5_850), T0)

        val matched = BillsCalculator.overview(listOf(b), listOf(bank), listOf(due), listOf(match), listOf(entry(expense)), today)
        val occurrence = matched.bills.single().occurrences.first { it.dueDate == due.dueDate }
        assertEquals(MatchState.MATCHED, occurrence.progress.state)
        assertEquals(1, occurrence.payments.size)

        val undone = LedgerProjector.project(listOf(expense), emptyList(), listOf(VoidMarker(expense.id, T0)))
        val reopened = BillsCalculator.overview(listOf(b), listOf(bank), listOf(due), listOf(match), undone, today)
        assertEquals(MatchState.NOT_MATCHED, reopened.bills.single().occurrences.first { it.dueDate == due.dueDate }.progress.state)
    }

    @Test
    fun stoppedBillsHaveNoNextDateAndLeaveTotals() {
        val overview = BillsCalculator.overview(listOf(bill(status = BillStatus.STOPPED)), listOf(bank), emptyList(), emptyList(), emptyList(), today)
        assertNull(overview.next)
        assertTrue(overview.annual.isEmpty())
        assertNull(overview.bills.single().next)
    }

    @Test
    fun totalsStayPerCurrency() {
        val overview = BillsCalculator.overview(
            listOf(bill("lkr"), bill("usd", amount = usd(10))),
            listOf(bank), emptyList(), emptyList(), emptyList(), today,
        )
        assertEquals(listOf(lkr(70_200), usd(120)), overview.annual)
    }

    @Test
    fun candidatesAreNearbyUnlinkedExpensesInTheSameCurrency() {
        val b = bill(firstDue = LocalDate.of(2026, 10, 10))
        val due = BillSchedule.expectedPayment(b, LocalDate.of(2026, 10, 10))
        val other = ExpectedPayment(ExpectedPaymentId("other"), Direction.OUT, lkr(1_000), LocalDate.of(2026, 10, 10), "Other", ExpectedSource.Manual, createdAt = T0)
        val candidates = BillsCalculator.paymentCandidates(
            due,
            listOf(other),
            listOf(PaymentMatch(other.id, TransactionId("partly"), lkr(1_000), T0)),
            listOf(
                entry(tx("close", TransactionType.EXPENSE, lkr(5_850), date = LocalDate.of(2026, 10, 11))),
                entry(tx("partly", TransactionType.EXPENSE, lkr(3_000), date = LocalDate.of(2026, 10, 9))),
                entry(tx("far", TransactionType.EXPENSE, lkr(5_850), date = LocalDate.of(2026, 12, 1))),
                entry(tx("income", TransactionType.INCOME, lkr(5_850), date = LocalDate.of(2026, 10, 10))),
                entry(tx("usd", TransactionType.EXPENSE, usd(10), date = LocalDate.of(2026, 10, 10))),
                entry(tx("undone", TransactionType.EXPENSE, lkr(5_850), date = LocalDate.of(2026, 10, 10)), voided = true),
            ),
        )
        assertEquals(listOf("close" to lkr(5_850), "partly" to lkr(2_000)), candidates.map { it.entry.id.value to it.available })
    }

    // --- Rules, linking and storage --------------------------------------------------------

    @Test
    fun billFormValidation() {
        val valid = BillRules.checkBill(" Dialog ", bank, "5,850", BillFrequency.MONTHLY, today) as BillCheck.Valid
        assertEquals("Dialog", valid.value.name)
        assertEquals(lkr(5_850), valid.value.amount)
        val invalid = BillRules.checkBill("", null, "", BillFrequency.MONTHLY, today) as BillCheck.Invalid
        assertEquals(setOf(BillProblem.NAME_MISSING, BillProblem.ACCOUNT_MISSING, BillProblem.AMOUNT_MISSING), invalid.problems)
        assertEquals(setOf(BillProblem.AMOUNT_INVALID), (BillRules.checkBill("x", bank, "0", BillFrequency.MONTHLY, today) as BillCheck.Invalid).problems)
        assertEquals(setOf(BillProblem.MORE_THAN_REMAINING), (BillRules.checkLink("6000", lkr(10_000), lkr(5_850)) as BillCheck.Invalid).problems)
    }

    private class FakeExpected : ExpectedPaymentRepository {
        val payments = MutableStateFlow<List<ExpectedPayment>>(emptyList())
        val matches = MutableStateFlow<List<PaymentMatch>>(emptyList())
        override fun observeExpectedPayments(): Flow<List<ExpectedPayment>> = payments
        override fun observeMatches(): Flow<List<PaymentMatch>> = matches
        override suspend fun saveExpectedPayment(payment: ExpectedPayment) { payments.value = payments.value.filterNot { it.id == payment.id } + payment }
        override suspend fun saveMatch(match: PaymentMatch) { if (matches.value.none { it.id == match.id }) matches.value = matches.value + match }
        override suspend fun removeMatch(expectedPaymentId: ExpectedPaymentId, transactionId: TransactionId) {
            matches.value = matches.value.filterNot { it.expectedPaymentId == expectedPaymentId && it.transactionId == transactionId }
        }
    }

    @Test
    fun linkingSavesTheDueDateOnceAndTheMatch() = runBlocking {
        val repo = FakeExpected()
        val b = bill(firstDue = LocalDate.of(2026, 10, 10))
        val expense = entry(tx("e1", TransactionType.EXPENSE, lkr(5_850), date = LocalDate.of(2026, 10, 10)))
        val overview = BillsCalculator.overview(listOf(b), listOf(bank), emptyList(), emptyList(), listOf(expense), today)
        val occurrence = overview.bills.single().occurrences.first { it.dueDate == LocalDate.of(2026, 10, 10) }
        val candidate = BillsCalculator.paymentCandidates(occurrence.progress.expected, emptyList(), emptyList(), listOf(expense)).single()

        val link = LinkBillPaymentUseCase(repo)
        assertTrue(link(b, occurrence, candidate, lkr(6_000), T0).exceptionOrNull() is BillError.AmountTooHigh)
        assertTrue(link(b, occurrence, candidate, lkr(5_850), T0).isSuccess)
        assertEquals(listOf(BillSchedule.occurrenceId(b, LocalDate.of(2026, 10, 10))), repo.payments.value.map { it.id })
        assertEquals(1, repo.matches.value.size)
    }

    @Test
    fun billRoundTripsThroughFirestoreMaps() {
        val b = bill(frequency = BillFrequency.YEARLY, status = BillStatus.STOPPED)
        assertEquals(b, BillDocumentMapper.fromMap(BillDocumentMapper.toMap(b)))
        assertNull(BillDocumentMapper.fromMap(mapOf("id" to "b1")))
    }
}
