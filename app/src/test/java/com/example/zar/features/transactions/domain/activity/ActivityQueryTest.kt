package com.example.zar.features.transactions.domain.activity

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.ActivitySnapshot
import com.example.zar.features.transactions.domain.activity.model.DatePreset
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityQueryTest {
    private val today = LocalDate.of(2026, 10, 24)

    private fun day(month: Int, d: Int) = LocalDate.of(2026, month, d)

    private fun withText(e: LedgerEntry, merchant: String? = null, note: String? = null): LedgerEntry {
        val tx = e.original.copy(merchant = merchant, note = note)
        return LedgerEntry(tx, tx, emptyList(), e.voidMarker, emptyList())
    }

    private val keells = withText(entry(tx("keells", TransactionType.EXPENSE, lkr(8_400), cash, day(10, 24))), "Keells Super", "Weekly groceries")
    private val pickMe = withText(entry(tx("pickme", TransactionType.EXPENSE, lkr(1_250), cash, day(10, 20))), "PickMe")
    private val client = withText(entry(tx("client", TransactionType.INCOME, lkr(125_000), bank, day(10, 24))), "Client payment")
    private val netflix = withText(entry(tx("netflix", TransactionType.EXPENSE, lkr(2_190), bank, day(9, 17))), "Netflix")
    private val voidedCargills = withText(entry(tx("cargills", TransactionType.EXPENSE, lkr(3_240), cash, day(10, 21)), voided = true), "Cargills")

    private val snapshot = ActivitySnapshot(listOf(netflix, pickMe, keells, client, voidedCargills), listOf(bank, cash))

    private fun ids(filter: ActivityFilter = ActivityFilter()) =
        ActivityQuery.apply(snapshot, filter, today).rows.map { it.entry.id.value }

    @Test
    fun theDefaultIsThisMonthNewestFirstWithVoidedHidden() {
        // keells and client share a day; the sort is stable by id after the creation time.
        assertEquals(listOf("client", "keells", "pickme"), ids())
    }

    @Test
    fun lastSevenDaysIncludesTodayAndTheSixDaysBefore() {
        val filter = ActivityFilter(preset = DatePreset.LAST_7_DAYS)
        assertEquals(day(10, 18)..day(10, 24), ActivityQuery.rangeOf(filter, today))
        assertEquals(listOf("client", "keells", "pickme"), ids(filter))
    }

    @Test
    fun lastMonthIsTheWholePreviousCalendarMonth() {
        val filter = ActivityFilter(preset = DatePreset.LAST_MONTH)
        assertEquals(day(9, 1)..day(9, 30), ActivityQuery.rangeOf(filter, today))
        assertEquals(listOf("netflix"), ids(filter))
    }

    @Test
    fun aCustomRangeIsInclusiveAndAcceptsReversedDates() {
        val filter = ActivityFilter(preset = DatePreset.CUSTOM, customFrom = day(10, 24), customTo = day(10, 20))
        assertEquals(day(10, 20)..day(10, 24), ActivityQuery.rangeOf(filter, today))
        assertEquals(listOf("client", "keells", "pickme"), ids(filter))
    }

    @Test
    fun anUnfinishedCustomRangeFallsBackToThisMonth() {
        val filter = ActivityFilter(preset = DatePreset.CUSTOM, customFrom = day(10, 20))
        assertEquals(day(10, 1)..day(10, 31), ActivityQuery.rangeOf(filter, today))
    }

    @Test
    fun filtersByAccount() {
        assertEquals(listOf("keells", "pickme"), ids(ActivityFilter(accountId = cash.id)))
        assertEquals(listOf("client"), ids(ActivityFilter(accountId = bank.id)))
    }

    @Test
    fun voidedEntriesAppearOnlyWhenAskedFor() {
        assertFalse("cargills" in ids())
        assertEquals(listOf("client", "keells", "cargills", "pickme"), ids(ActivityFilter(showVoided = true)))
    }

    @Test
    fun searchMatchesTitleDescriptionAndAccountNameIgnoringCase() {
        assertEquals(listOf("keells"), ids(ActivityFilter(query = "KEELLS")))
        assertEquals(listOf("keells"), ids(ActivityFilter(query = "weekly")))
        assertEquals(listOf("keells", "pickme"), ids(ActivityFilter(query = "cash")))
        assertEquals(emptyList<String>(), ids(ActivityFilter(query = "zzz")))
    }

    @Test
    fun searchAlsoFindsWhatARowShowsWithoutATitleOfItsOwn() {
        val out = entry(tx("t-out", TransactionType.TRANSFER_OUT, lkr(5_000), bank, day(10, 22), transferId = "t"))
        val dining = withText(entry(tx("dine", TransactionType.EXPENSE, lkr(900), cash, day(10, 22))), "Pilawoos").let {
            val t = it.original.copy(categoryId = "coffee-dining")
            LedgerEntry(t, t, emptyList(), null, emptyList())
        }
        val all = ActivitySnapshot(listOf(out, dining, keells), listOf(bank, cash))

        fun found(q: String) = ActivityQuery.apply(all, ActivityFilter(query = q), today).rows.map { it.entry.id.value }

        assertEquals(listOf("t-out"), found("transfer"))
        assertEquals(listOf("dine"), found("coffee dining"))
        assertEquals(listOf("dine"), found("coffee"))
        assertEquals(listOf("keells", "dine"), found("expense"))
    }

    @Test
    fun summaryFollowsTheSameRangeAndAccountAndIgnoresVoidedEntries() {
        val all = ActivityQuery.apply(snapshot, ActivityFilter(showVoided = true), today).summary
        assertEquals(lkr(125_000), all.received.amount)
        // keells + pickme only: the voided Cargills purchase and last month's Netflix are not spending.
        assertEquals(lkr(9_650), all.spent.total.amount)

        val cashOnly = ActivityQuery.apply(snapshot, ActivityFilter(accountId = cash.id), today).summary
        assertEquals(lkr(0), cashOnly.received.amount)
        assertEquals(lkr(9_650), cashOnly.spent.total.amount)
    }

    @Test
    fun transfersAreListedButNeverCountAsReceivedOrSpent() {
        val out = entry(tx("t-out", TransactionType.TRANSFER_OUT, lkr(5_000), bank, day(10, 22), transferId = "t"))
        val inn = entry(tx("t-in", TransactionType.TRANSFER_IN, lkr(5_000), cash, day(10, 22), transferId = "t"))
        val result = ActivityQuery.apply(ActivitySnapshot(listOf(out, inn), listOf(bank, cash)), ActivityFilter(), today)

        assertEquals(2, result.rows.size)
        assertEquals(lkr(0), result.summary.received.amount)
        assertEquals(lkr(0), result.summary.spent.total.amount)
    }

    @Test
    fun noEntriesAtAllIsDifferentFromNoMatches() {
        val empty = ActivityQuery.apply(ActivitySnapshot(emptyList(), listOf(cash)), ActivityFilter(), today)
        assertFalse(empty.hasAnyEntries)

        val noMatch = ActivityQuery.apply(snapshot, ActivityFilter(query = "zzz"), today)
        assertTrue(noMatch.hasAnyEntries)
        assertTrue(noMatch.rows.isEmpty())
    }

    @Test
    fun aRowKnowsItsAccountAndToleratesAMissingOne() {
        val result = ActivityQuery.apply(ActivitySnapshot(listOf(keells), emptyList()), ActivityFilter(), today)
        assertEquals(null, result.rows.single().account)

        val named = ActivityQuery.apply(snapshot, ActivityFilter(), today).rows.first { it.entry.id.value == "keells" }
        assertEquals(cash, named.account)
    }

    @Test
    fun activeCountCountsOnlyChoicesThatDifferFromTheDefault() {
        assertEquals(0, ActivityFilter().activeCount)
        assertEquals(3, ActivityFilter(accountId = cash.id, preset = DatePreset.LAST_MONTH, showVoided = true).activeCount)
        assertEquals(1, ActivityFilter(query = "x").activeCount)
        assertEquals(0, ActivityFilter(query = "  ").activeCount)
    }

    @Test
    fun theCreationTimeBreaksTiesBetweenEntriesOnTheSameDay() {
        val early = entry(tx("a", TransactionType.EXPENSE, lkr(1), cash, day(10, 24)))
        val lateTx = early.original.copy(id = com.example.zar.core.domain.model.TransactionId("b"), createdAt = T0.plusSeconds(10))
        val late = LedgerEntry(lateTx, lateTx, emptyList(), null, emptyList())
        val rows = ActivityQuery.apply(ActivitySnapshot(listOf(early, late), listOf(cash)), ActivityFilter(), today).rows
        assertEquals(listOf("b", "a"), rows.map { it.entry.id.value })
    }
}
