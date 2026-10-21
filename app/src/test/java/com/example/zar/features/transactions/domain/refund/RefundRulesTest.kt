package com.example.zar.features.transactions.domain.refund

import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.TestLedger.usdRate
import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.ReportingConversion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundRulesTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val purchase = entry(tx("p1", TransactionType.EXPENSE, lkr(8_400), cash))

    private fun refundEntry(id: String, amount: Long, voided: Boolean = false, of: String = "p1") =
        entry(tx(id, TransactionType.REFUND, lkr(amount), cash, refundOf = of), voided)

    private fun input(amount: String, date: LocalDate = today, note: String = "") = RefundFormInput(amount, date, note)

    private fun invalid(result: RefundFormResult) = (result as RefundFormResult.Invalid).problems

    @Test
    fun aPurchaseWithNoRefundsHasEverythingLeft() {
        val summary = RefundRules.summary(purchase, listOf(purchase))

        assertEquals(lkr(0), summary.refunded)
        assertEquals(lkr(8_400), summary.remaining)
        assertFalse(summary.hasRefunds)
        assertTrue(summary.canRefund)
    }

    @Test
    fun refundsAddUpAndTheRestIsStillRefundable() {
        val entries = listOf(purchase, refundEntry("r1", 2_000), refundEntry("r2", 400))

        val summary = RefundRules.summary(purchase, entries)

        assertEquals(lkr(2_400), summary.refunded)
        assertEquals(lkr(6_000), summary.remaining)
        assertEquals(listOf("r1", "r2"), summary.refunds.map { it.id.value })
    }

    @Test
    fun aVoidedRefundNoLongerCountsAndOtherPurchasesAreIgnored() {
        val entries = listOf(
            purchase,
            refundEntry("r1", 2_000, voided = true),
            refundEntry("r2", 500),
            entry(tx("p2", TransactionType.EXPENSE, lkr(300), cash)),
            refundEntry("r3", 300, of = "p2"),
        )

        val summary = RefundRules.summary(purchase, entries)

        assertEquals(lkr(500), summary.refunded)
        assertEquals(lkr(7_900), summary.remaining)
    }

    @Test
    fun aFullyRefundedPurchaseCannotTakeAnotherRefund() {
        val summary = RefundRules.summary(purchase, listOf(purchase, refundEntry("r1", 8_400)))

        assertEquals(lkr(0), summary.remaining)
        assertFalse(summary.canRefund)
        assertTrue(RefundProblem.NOTHING_LEFT in invalid(RefundRules.validate(summary, cash, input("1"), today)))
    }

    @Test
    fun refundsCannotAddUpToMoreThanThePurchase() {
        val summary = RefundRules.summary(purchase, listOf(purchase, refundEntry("r1", 2_000)))

        assertTrue(RefundProblem.AMOUNT_TOO_HIGH in invalid(RefundRules.validate(summary, cash, input("6,400.01"), today)))
        assertTrue(RefundRules.validate(summary, cash, input("6,400"), today) is RefundFormResult.Valid)
    }

    @Test
    fun aPurchaseCorrectedBelowWhatWasRefundedHasNothingLeftNotANegativeAmount() {
        val corrected = purchase.copy(current = purchase.current.copy(amount = lkr(1_000)))

        val summary = RefundRules.summary(corrected, listOf(corrected, refundEntry("r1", 2_000)))

        assertEquals(lkr(0), summary.remaining)
        assertFalse(summary.canRefund)
    }

    @Test
    fun aVoidedOrNonExpenseEntryCannotBeRefunded() {
        val voided = entry(tx("p1", TransactionType.EXPENSE, lkr(100), cash), voided = true)
        val income = entry(tx("i1", TransactionType.INCOME, lkr(100), cash))

        assertFalse(RefundRules.summary(voided, listOf(voided)).canRefund)
        assertFalse(RefundRules.summary(income, listOf(income)).canRefund)
    }

    @Test
    fun theFormNeedsAValidAmountAndADateThatIsNotInTheFuture() {
        val summary = RefundRules.summary(purchase, listOf(purchase))

        assertEquals(setOf(RefundProblem.AMOUNT_MISSING), invalid(RefundRules.validate(summary, cash, input(""), today)))
        assertEquals(setOf(RefundProblem.AMOUNT_INVALID), invalid(RefundRules.validate(summary, cash, input("abc"), today)))
        assertEquals(setOf(RefundProblem.AMOUNT_INVALID), invalid(RefundRules.validate(summary, cash, input("0"), today)))
        assertEquals(setOf(RefundProblem.AMOUNT_INVALID), invalid(RefundRules.validate(summary, cash, input("-5"), today)))
        assertEquals(setOf(RefundProblem.AMOUNT_INVALID), invalid(RefundRules.validate(summary, cash, input("1.234"), today)))
        assertEquals(
            setOf(RefundProblem.DATE_IN_FUTURE),
            invalid(RefundRules.validate(summary, cash, input("10", date = today.plusDays(1)), today)),
        )
        assertEquals(
            setOf(RefundProblem.NOTE_TOO_LONG),
            invalid(RefundRules.validate(summary, cash, input("10", note = "x".repeat(501)), today)),
        )
    }

    @Test
    fun aValidFormKeepsTheAmountDateAndATrimmedNote() {
        val summary = RefundRules.summary(purchase, listOf(purchase))

        val valid = RefundRules.validate(summary, cash, input("2,000", note = "  Damaged item  "), today) as RefundFormResult.Valid

        assertEquals(lkr(2_000), valid.details.amount)
        assertEquals("Damaged item", valid.details.note)
        assertEquals(today, valid.details.date)
        assertNull((RefundRules.validate(summary, cash, input("2,000", note = "   "), today) as RefundFormResult.Valid).details.note)
    }

    @Test
    fun theRefundIsALinkedRefundOnThePurchasesAccountAndNeverIncome() {
        val summary = RefundRules.summary(purchase, listOf(purchase))
        val details = (RefundRules.validate(summary, cash, input("2,000"), today) as RefundFormResult.Valid).details

        val refund = RefundRules.newRefund(TransactionId("r1"), details, T0)

        assertEquals(TransactionType.REFUND, refund.type)
        assertEquals(TransactionId("p1"), refund.refundOf)
        assertEquals(cash.id, refund.accountId)
        assertEquals(lkr(2_000), refund.amount)
        assertNull(refund.spendingClass)
        assertNull(refund.categoryId)
        assertEquals(ReportingConversion.SameCurrency, refund.reportingConversion)
    }

    @Test
    fun aForeignCurrencyRefundTakesBackAtThePurchasesOwnRate() {
        val usdPurchase = entry(tx("p9", TransactionType.EXPENSE, usd(100), payoneer, date = OCT_10))
        val summary = RefundRules.summary(usdPurchase, listOf(usdPurchase))
        val details = (RefundRules.validate(summary, payoneer, input("40"), today) as RefundFormResult.Valid).details

        val refund = RefundRules.newRefund(TransactionId("r9"), details, T0)

        assertEquals(CurrencyConverter.toReporting(usd(40), usdRate), refund.reportingConversion)
        assertEquals(lkr(12_000), CurrencyConverter.reportingAmount(refund.amount, refund.reportingConversion))
    }
}
