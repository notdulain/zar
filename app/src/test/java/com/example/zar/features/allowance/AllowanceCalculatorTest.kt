package com.example.zar.features.allowance

import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.allowance.domain.AllowanceCalculator
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.model.AllowanceStatus
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowanceCalculatorTest {

    // Wednesday 21 Oct 2026: the default week is Monday 19 to Sunday 25 October.
    private val today = LocalDate.of(2026, 10, 21)
    private val monday = LocalDate.of(2026, 10, 19)
    private val sunday = LocalDate.of(2026, 10, 25)
    private val settings = AllowanceSettings(isEnabled = true, amount = lkr(10_000))

    private fun discretionary(
        id: String,
        amount: Long = 1_000,
        date: LocalDate = today,
        category: String? = null,
        conversion: ReportingConversion? = null,
    ): Transaction = tx(id, TransactionType.EXPENSE, lkr(amount), date = date, conversion = conversion)
        .copy(categoryId = category)

    /** The shared fixtures date records 10 Oct; these tests need them inside the week being checked. */
    private fun Transaction.onToday(): Transaction = copy(date = today)

    private fun status(
        vararg transactions: Transaction,
        settings: AllowanceSettings = this.settings,
        corrections: List<Correction> = emptyList(),
        voids: List<VoidMarker> = emptyList(),
        expected: List<ExpectedPayment> = emptyList(),
        matches: List<PaymentMatch> = emptyList(),
    ): AllowanceStatus = AllowanceCalculator.status(
        settings,
        LedgerProjector.project(transactions.toList(), corrections, voids),
        expected,
        matches,
        today,
    )!!

    // --- Settings ----------------------------------------------------------------------

    @Test
    fun startsDisabledAndShowsNothing() {
        assertFalse(AllowanceSettings.Disabled.isEnabled)
        assertNull(AllowanceCalculator.status(AllowanceSettings.Disabled, emptyList(), emptyList(), emptyList(), today))
    }

    @Test
    fun switchedOffKeepsItsAmountButShowsNothing() {
        val off = settings.copy(isEnabled = false)
        assertEquals(lkr(10_000), off.amount)
        assertNull(AllowanceCalculator.status(off, emptyList(), emptyList(), emptyList(), today))
    }

    @Test
    fun settingsRejectInvalidCombinations() {
        assertThrows(IllegalArgumentException::class.java) { AllowanceSettings(isEnabled = true, amount = null) }
        assertThrows(IllegalArgumentException::class.java) { AllowanceSettings(isEnabled = true, amount = lkr(0)) }
        assertThrows(IllegalArgumentException::class.java) {
            AllowanceSettings(isEnabled = true, amount = TestLedger.usd(100))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AllowanceSettings(isEnabled = true, amount = lkr(100), categoryIds = (1..9).map { "c$it" }.toSet())
        }
    }

    @Test
    fun defaultWeekStartsOnMonday() {
        assertEquals(DayOfWeek.MONDAY, AllowanceSettings.Disabled.weekStart)
        val week = status().week
        assertEquals(monday, week.start)
        assertEquals(sunday, week.endInclusive)
    }

    // --- Remaining and overspent --------------------------------------------------------

    @Test
    fun remainingIsTheAllowanceMinusEligibleSpending() {
        val s = status(discretionary("a", 1_500), discretionary("b", 2_000))
        assertEquals(lkr(3_500), s.spent.amount)
        assertEquals(lkr(6_500), s.remaining)
        assertFalse(s.isOverspent)
        assertEquals(lkr(0), s.overspentBy)
    }

    @Test
    fun overspendingIsNegativeRemainingAndNamedAsSuch() {
        val s = status(discretionary("a", 7_000), discretionary("b", 4_000))
        assertEquals(lkr(-1_000), s.remaining)
        assertTrue(s.isOverspent)
        assertEquals(lkr(1_000), s.overspentBy)
    }

    @Test
    fun spendingExactlyTheAllowanceIsNotOverspent() {
        val s = status(discretionary("a", 10_000))
        assertEquals(lkr(0), s.remaining)
        assertFalse(s.isOverspent)
    }

    // --- What does not count -------------------------------------------------------------

    @Test
    fun committedBillsTransfersIncomeAndAdjustmentsAreNeverSpending() {
        val s = status(
            tx("rent", TransactionType.EXPENSE, lkr(30_000), spendingClass = SpendingClass.COMMITTED).onToday(),
            tx("salary", TransactionType.INCOME, lkr(80_000)).onToday(),
            tx("out", TransactionType.TRANSFER_OUT, lkr(5_000), transferId = "t1").onToday(),
            tx("in", TransactionType.TRANSFER_IN, lkr(5_000), account = TestLedger.cash, transferId = "t1").onToday(),
            tx("adj-in", TransactionType.ADJUSTMENT_IN, lkr(700)).onToday(),
            tx("adj-out", TransactionType.ADJUSTMENT_OUT, lkr(900)).onToday(),
        )
        assertEquals(lkr(0), s.spent.amount)
        assertEquals(lkr(10_000), s.remaining)
    }

    @Test
    fun incomeNeverIncreasesTheAllowance() {
        val without = status(discretionary("a", 2_000))
        val with = status(discretionary("a", 2_000), tx("gift", TransactionType.INCOME, lkr(500_000)).onToday())
        assertEquals(without.remaining, with.remaining)
        assertEquals(lkr(8_000), with.remaining)
    }

    @Test
    fun anExpenseLinkedToABillIsAConfirmedBillNotAllowanceSpending() {
        val occurrence = ExpectedPayment(
            id = ExpectedPaymentId("bill-dialog-2026-10-21"),
            direction = Direction.OUT,
            amount = lkr(2_450),
            dueDate = today,
            title = "Dialog",
            source = ExpectedSource.BillOccurrence("dialog", today),
            createdAt = TestLedger.T0,
        )
        val s = status(
            discretionary("dialog-payment", 2_450),
            discretionary("coffee", 600),
            expected = listOf(occurrence),
            matches = listOf(PaymentMatch(occurrence.id, TransactionId("dialog-payment"), lkr(2_450), TestLedger.T0)),
        )
        assertEquals(lkr(600), s.spent.amount)
    }

    @Test
    fun aFreelanceMatchDoesNotHideAnExpense() {
        val milestone = ExpectedPayment(
            ExpectedPaymentId("m1"), Direction.IN, lkr(1_000), today, "Milestone",
            ExpectedSource.FreelanceMilestone("p1", "m1"), createdAt = TestLedger.T0,
        )
        val s = status(
            discretionary("lunch", 800),
            expected = listOf(milestone),
            matches = listOf(PaymentMatch(milestone.id, TransactionId("lunch"), lkr(800), TestLedger.T0)),
        )
        assertEquals(lkr(800), s.spent.amount)
    }

    // --- Coverage ------------------------------------------------------------------------

    @Test
    fun unclassifiedExpensesAreLeftOutAndReportedNotGuessed() {
        val s = status(
            discretionary("a", 1_000),
            tx("mystery", TransactionType.EXPENSE, lkr(4_000), spendingClass = SpendingClass.UNKNOWN).onToday(),
        )
        assertEquals(lkr(1_000), s.spent.amount)
        assertEquals(1, s.unclassifiedExpenseCount)
        assertTrue(s.hasCoverageWarning)
    }

    @Test
    fun cashCatchUpOutflowsAreReportedNotCounted() {
        val s = status(tx("catch-up", TransactionType.ADJUSTMENT_OUT, lkr(2_500)).onToday())
        assertEquals(lkr(0), s.spent.amount)
        assertEquals(1, s.adjustmentCount)
        assertTrue(s.hasCoverageWarning)
    }

    @Test
    fun anExpenseWithNoLkrRateIsLeftOutAndFlagged() {
        val s = status(
            discretionary("known", 1_000),
            discretionary("foreign", 500, conversion = ReportingConversion.Missing(Currencies.LKR)),
        )
        assertEquals(lkr(1_000), s.spent.amount)
        assertEquals(1, s.missingRateCount)
        assertTrue(s.hasCoverageWarning)
    }

    @Test
    fun aCleanWeekHasNoCoverageWarning() {
        assertFalse(status(discretionary("a", 1_000)).hasCoverageWarning)
    }

    // --- Week boundaries ----------------------------------------------------------------

    @Test
    fun onlyThisWeeksSpendingCountsAndBothEdgesAreIncluded() {
        val s = status(
            discretionary("last-sunday", 9_000, date = monday.minusDays(1)),
            discretionary("monday", 1_000, date = monday),
            discretionary("sunday", 2_000, date = sunday),
            discretionary("next-monday", 9_000, date = sunday.plusDays(1)),
        )
        assertEquals(lkr(3_000), s.spent.amount)
    }

    @Test
    fun theWeekStartIsChosenByTheUser() {
        val saturdayWeeks = settings.copy(weekStart = DayOfWeek.SATURDAY)
        val s = status(
            discretionary("friday-before", 9_000, date = LocalDate.of(2026, 10, 16)),
            discretionary("saturday", 1_000, date = LocalDate.of(2026, 10, 17)),
            discretionary("friday-after", 2_000, date = LocalDate.of(2026, 10, 23)),
            discretionary("saturday-after", 9_000, date = LocalDate.of(2026, 10, 24)),
            settings = saturdayWeeks,
        )
        assertEquals(LocalDate.of(2026, 10, 17), s.week.start)
        assertEquals(LocalDate.of(2026, 10, 23), s.week.endInclusive)
        assertEquals(lkr(3_000), s.spent.amount)
    }

    @Test
    fun thereIsNoRolloverFromAQuietWeek() {
        val s = status(discretionary("old", 1_000, date = monday.minusDays(7)))
        assertEquals(lkr(10_000), s.remaining)
    }

    // --- Categories ----------------------------------------------------------------------

    @Test
    fun chosenCategoriesLimitWhatCounts() {
        val limited = settings.copy(categoryIds = setOf("coffee-dining"))
        val s = status(
            discretionary("coffee", 800, category = "coffee-dining"),
            discretionary("shop", 5_000, category = "groceries"),
            discretionary("none", 700, category = null),
            settings = limited,
        )
        assertEquals(lkr(800), s.spent.amount)
    }

    @Test
    fun noChosenCategoriesCountsEveryDiscretionaryCategory() {
        val s = status(
            discretionary("coffee", 800, category = "coffee-dining"),
            discretionary("shop", 5_000, category = "groceries"),
            discretionary("none", 700, category = null),
        )
        assertEquals(lkr(6_500), s.spent.amount)
    }

    // --- Refunds, corrections and undo ---------------------------------------------------

    @Test
    fun aLinkedRefundReducesThePurchaseInThePurchasesWeek() {
        val s = status(
            discretionary("shoes", 4_000),
            tx("refund", TransactionType.REFUND, lkr(1_500), refundOf = "shoes").onToday(),
        )
        assertEquals(lkr(2_500), s.spent.amount)
    }

    @Test
    fun aRefundNeverGoesBelowTheOriginalPurchase() {
        val s = status(
            discretionary("shoes", 1_000),
            tx("refund", TransactionType.REFUND, lkr(5_000), refundOf = "shoes").onToday(),
        )
        assertEquals(lkr(0), s.spent.amount)
        assertEquals(lkr(10_000), s.remaining)
    }

    @Test
    fun aRefundThisWeekForALastWeekPurchaseDoesNotChangeThisWeek() {
        val s = status(
            discretionary("old-shoes", 4_000, date = monday.minusDays(3)),
            discretionary("lunch", 500),
            tx("refund", TransactionType.REFUND, lkr(4_000), refundOf = "old-shoes").onToday(),
        )
        assertEquals(lkr(500), s.spent.amount)
    }

    @Test
    fun anUnrelatedRefundOnlyAffectsItsOwnPurchase() {
        val s = status(
            discretionary("a", 2_000),
            discretionary("b", 3_000),
            tx("refund", TransactionType.REFUND, lkr(1_000), refundOf = "b").onToday(),
        )
        assertEquals(lkr(4_000), s.spent.amount)
    }

    @Test
    fun correctionsAreAppliedAndUndoneExpensesDropOut() {
        val s = status(
            discretionary("a", 1_000),
            discretionary("b", 2_000),
            discretionary("c", 4_000),
            corrections = listOf(TestLedger.correction("fix", "a", TransactionChanges(amount = lkr(1_800)))),
            voids = listOf(TestLedger.void("c")),
        )
        assertEquals(lkr(3_800), s.spent.amount)
    }

    @Test
    fun aCorrectionThatMovesTheDateMovesItBetweenWeeks() {
        val s = status(
            discretionary("a", 1_000),
            corrections = listOf(
                TestLedger.correction("move", "a", TransactionChanges(date = monday.minusDays(2))),
            ),
        )
        assertEquals(lkr(0), s.spent.amount)
    }
}
