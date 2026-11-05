package com.example.zar.scenario

import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ReportingConversion
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kavindu earns from four channels in two currencies (assignment §3.2). These tests check that
 * "Received this month" is what actually arrived, never an estimate or a guess.
 */
class KavinduIncomeScenarioTest {

    private val october = YearMonth.of(2026, 10)
    private val period = october.atDay(1)..october.atEndOfMonth()
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)

    private fun salary(amount: Long, date: LocalDate = oct(25)) = entry(tx("salary-$date", TransactionType.INCOME, lkr(amount), date = date))
    private fun freelance(id: String, amount: Long, date: LocalDate) = entry(tx(id, TransactionType.INCOME, lkr(amount), date = date))

    /** AdSense pays in USD; the rate is the one Kavindu saw on the day (USD 1 = LKR 300 in the fixture). */
    private fun adsense(id: String, usdAmount: Long, date: LocalDate, rateKnown: Boolean = true) = entry(
        tx(id, TransactionType.INCOME, usd(usdAmount), account = com.example.zar.core.domain.TestLedger.payoneer, date = date,
            conversion = if (rateKnown) null else ReportingConversion.Missing(Currencies.LKR)),
    )

    @Test
    fun allFourChannelsAddUpToOneRealMonthlyFigure() {
        val entries = listOf(
            salary(132_500),                         // base + performance component, on the 25th
            freelance("react-sme", 45_000, oct(9)),  // a milestone for a React build
            adsense("adsense-oct", 40, oct(3)),      // USD 40 × 300 = LKR 12,000
        )
        val received = LedgerSummaryCalculator.summarize(entries, period).received

        // 132,500 + 45,000 + 12,000. Inside his guess of "160,000–210,000", but now an exact figure.
        assertEquals(lkr(189_500), received.amount)
        assertTrue("Every amount has a known LKR value", received.isComplete)
    }

    @Test
    fun usdIncomeWithoutARateIsCountedAsUnknownNotGuessed() {
        val entries = listOf(salary(120_000), adsense("adsense-oct", 60, oct(3), rateKnown = false))
        val summary = LedgerSummaryCalculator.summarize(entries, period)

        assertEquals("Only the salary has an LKR value", lkr(120_000), summary.received.amount)
        assertEquals("The AdSense payment is flagged, not converted at a made-up rate", 1, summary.received.unresolvedCount)
        assertTrue("Home warns the total is incomplete", summary.hasIncompleteData)
    }

    @Test
    fun variableSalaryIsTheAmountThatArrivedEachMonth() {
        val september = YearMonth.of(2026, 9)
        val entries = listOf(salary(120_000, LocalDate.of(2026, 9, 25)), salary(140_000, oct(25)))

        val sep = LedgerSummaryCalculator.summarize(entries, september.atDay(1)..september.atEndOfMonth()).received
        val oct = LedgerSummaryCalculator.summarize(entries, period).received

        assertEquals(lkr(120_000), sep.amount)
        assertEquals("The bonus month shows the real, higher amount", lkr(140_000), oct.amount)
    }

    @Test
    fun movingMoneyBetweenHisOwnAccountsIsNotIncome() {
        // AdSense USD arrives in Payoneer (income), then he moves it to his LKR bank (a transfer).
        val entries = listOf(
            adsense("adsense-oct", 40, oct(3)),
            entry(tx("move-out", TransactionType.TRANSFER_OUT, usd(40), account = com.example.zar.core.domain.TestLedger.payoneer, date = oct(5), transferId = "t1")),
            entry(tx("move-in", TransactionType.TRANSFER_IN, lkr(12_000), date = oct(5), transferId = "t1")),
        )
        val received = LedgerSummaryCalculator.summarize(entries, period).received

        assertEquals("Counted once, when it was earned", lkr(12_000), received.amount)
    }

    @Test
    fun undoneIncomeLeavesTheTotal() {
        val entries = listOf(salary(130_000), entry(tx("typo", TransactionType.INCOME, lkr(45_000), date = oct(9)), voided = true))
        val summary = LedgerSummaryCalculator.summarize(entries, period)

        assertEquals(lkr(130_000), summary.received.amount)
        assertFalse(summary.hasIncompleteData)
    }
}
