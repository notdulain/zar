package com.example.zar.core.domain.calculation

import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedPaymentStatus
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpectedPaymentProgressCalculatorTest {

    private fun milestone(id: String, amount: Long, due: java.time.LocalDate = OCT_10.plusDays(5)) = ExpectedPayment(
        id = ExpectedPaymentId(id), direction = Direction.IN, amount = usd(amount), dueDate = due, title = id,
        source = ExpectedSource.FreelanceMilestone("project", id), createdAt = T0,
    )

    private fun match(expected: String, tx: String, amount: Long, second: Long = 0) =
        PaymentMatch(ExpectedPaymentId(expected), TransactionId(tx), usd(amount), T0.plusSeconds(second))

    private val receipt = tx("receipt", TransactionType.INCOME, usd(300), account = payoneer)

    @Test
    fun partialReceiptLeavesRemaining() {
        val progress = ExpectedPaymentProgressCalculator.calculate(
            listOf(milestone("m1", 500)), listOf(match("m1", "receipt", 300)), listOf(entry(receipt)), OCT_10,
        ).single()
        assertEquals(usd(300), progress.matched)
        assertEquals(usd(200), progress.remaining)
        assertEquals(MatchState.PARTIALLY_MATCHED, progress.state)
        assertEquals(usd(0), progress.overdue)
    }

    @Test
    fun onePaymentIsNeverCountedTwice() {
        val progress = ExpectedPaymentProgressCalculator.calculate(
            listOf(milestone("m1", 300), milestone("m2", 300)),
            listOf(match("m1", "receipt", 300, second = 1), match("m2", "receipt", 300, second = 2)),
            listOf(entry(receipt)),
            OCT_10,
        ).associateBy { it.expected.id.value }
        assertEquals(MatchState.MATCHED, progress.getValue("m1").state)
        assertEquals(MatchState.NOT_MATCHED, progress.getValue("m2").state)
    }

    @Test
    fun undoneReceiptStopsCountingAndOverdueShows() {
        val progress = ExpectedPaymentProgressCalculator.calculate(
            listOf(milestone("m1", 500, due = OCT_10.minusDays(1))),
            listOf(match("m1", "receipt", 300)),
            listOf(entry(receipt, voided = true)),
            OCT_10,
        ).single()
        assertEquals(MatchState.NOT_MATCHED, progress.state)
        assertEquals(usd(500), progress.overdue)
    }

    @Test
    fun cancelledIsNeverOverdue() {
        val cancelled = milestone("m1", 500, due = OCT_10.minusDays(1)).copy(status = ExpectedPaymentStatus.CANCELLED)
        val progress = ExpectedPaymentProgressCalculator.calculate(listOf(cancelled), emptyList(), emptyList(), OCT_10).single()
        assertEquals(MatchState.CANCELLED, progress.state)
        assertEquals(usd(0), progress.overdue)
    }
}
