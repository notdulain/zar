package com.example.zar.features.catchup.domain

import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.catchup.domain.model.UnresolvedKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnresolvedRulesTest {
    private val accounts = listOf(bank, cash, payoneer)

    private fun find(vararg entries: com.example.zar.core.domain.model.LedgerEntry) = UnresolvedRules.find(entries.toList(), accounts)

    @Test
    fun anExpenseWithUnknownSpendingIsListedAsUncategorised() {
        val items = find(entry(tx("e1", TransactionType.EXPENSE, spendingClass = SpendingClass.UNKNOWN)))

        assertEquals(listOf(UnresolvedKind.UNCATEGORISED), items.map { it.kind })
    }

    @Test
    fun classifiedExpensesIncomeTransfersAndRefundsAreNotListed() {
        val items = find(
            entry(tx("e1", TransactionType.EXPENSE, spendingClass = SpendingClass.DISCRETIONARY)),
            entry(tx("e2", TransactionType.EXPENSE, spendingClass = SpendingClass.COMMITTED)),
            entry(tx("i1", TransactionType.INCOME)),
            entry(tx("t1", TransactionType.TRANSFER_OUT, transferId = "tr")),
            entry(tx("r1", TransactionType.REFUND, refundOf = "e1")),
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun anOutgoingAdjustmentIsAnUnexplainedGapButAnIncomingOneIsNot() {
        val items = find(
            entry(tx("a1", TransactionType.ADJUSTMENT_OUT, account = cash)),
            entry(tx("a2", TransactionType.ADJUSTMENT_IN, account = cash)),
        )

        assertEquals(listOf("a1"), items.map { it.entryId.value })
        assertEquals(UnresolvedKind.UNEXPLAINED_ADJUSTMENT, items.single().kind)
    }

    @Test
    fun anExpenseOrIncomeWithoutARateIsListedAsMissingRate() {
        val missing = ReportingConversion.Missing(Currencies.LKR)
        val items = find(
            entry(tx("e1", TransactionType.EXPENSE, usd(10), payoneer, conversion = missing)),
            entry(tx("i1", TransactionType.INCOME, usd(10), payoneer, conversion = missing)),
        )

        assertEquals(setOf(UnresolvedKind.MISSING_RATE), items.map { it.kind }.toSet())
        assertEquals(2, items.size)
    }

    @Test
    fun anEntryIsListedOnceUnderTheFirstReasonThatApplies() {
        val missing = ReportingConversion.Missing(Currencies.LKR)
        // Missing rate and uncategorised at once: the rate comes first because it keeps the entry out of totals.
        val both = entry(tx("e1", TransactionType.EXPENSE, usd(10), payoneer, spendingClass = SpendingClass.UNKNOWN, conversion = missing))

        val items = find(both)

        assertEquals(1, items.size)
        assertEquals(UnresolvedKind.MISSING_RATE, items.single().kind)
    }

    @Test
    fun voidedEntriesAreNeverListed() {
        val items = find(entry(tx("e1", TransactionType.EXPENSE, spendingClass = SpendingClass.UNKNOWN), voided = true))

        assertTrue(items.isEmpty())
    }

    @Test
    fun itemsCarryTheirAccountAndAreListedNewestFirst() {
        val older = tx("e1", TransactionType.EXPENSE, account = cash, date = OCT_10.minusDays(3), spendingClass = SpendingClass.UNKNOWN)
        val newer = tx("e2", TransactionType.EXPENSE, account = bank, date = OCT_10, spendingClass = SpendingClass.UNKNOWN)

        val items = find(entry(older), entry(newer))

        assertEquals(listOf("e2", "e1"), items.map { it.entryId.value })
        assertEquals(bank, items.first().account)
        assertEquals(cash, items.last().account)
    }

    @Test
    fun anEntryOnAnUnknownAccountIsStillListedWithoutOne() {
        val orphan = tx("e1", TransactionType.EXPENSE, account = cash, spendingClass = SpendingClass.UNKNOWN)

        val items = UnresolvedRules.find(listOf(entry(orphan)), accounts = emptyList())

        assertNull(items.single().account)
    }

    @Test
    fun nothingRecordedGivesNothingToReview() {
        assertTrue(find().isEmpty())
        assertEquals(0, UnresolvedRules.recordedInLastWeek(emptyList(), LocalDate.of(2026, 10, 21)))
    }

    @Test
    fun recordedInLastWeekCountsTheSevenDaysUpToToday() {
        val today = LocalDate.of(2026, 10, 21)
        val entries = listOf(
            entry(tx("a", TransactionType.EXPENSE, date = today)),
            entry(tx("b", TransactionType.EXPENSE, date = today.minusDays(6))),
            // Seven days ago is outside the week, a future date is not recorded yet.
            entry(tx("c", TransactionType.EXPENSE, date = today.minusDays(7))),
            entry(tx("d", TransactionType.EXPENSE, date = today.plusDays(1))),
            entry(tx("e", TransactionType.EXPENSE, date = today), voided = true),
        )

        assertEquals(2, UnresolvedRules.recordedInLastWeek(entries, today))
    }
}
