package com.example.zar.features.home

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.features.home.domain.model.CumulativeIncomeSeries
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CumulativeIncomeSeriesTest {
    private val october = YearMonth.of(2026, 10)
    private val afterOctober = LocalDate.of(2026, 11, 5)

    private fun daily(month: YearMonth, vararg entries: Pair<Int, Long>): List<ReportingTotal> {
        val byDay = entries.toMap()
        return List(month.lengthOfMonth()) { ReportingTotal(lkr(byDay[it + 1] ?: 0L)) }
    }

    private fun YearMonth.series(days: List<ReportingTotal>, today: LocalDate = afterOctober) =
        CumulativeIncomeSeries.from(this, days, today)

    @Test
    fun zeroIncomeDaysCarryTheAccumulatedTotal() {
        val series = october.series(daily(october, 1 to 100L, 3 to 50L))
        assertEquals(listOf(lkr(100), lkr(100), lkr(150), lkr(150)), series.slots.take(4).map { it.cumulative.amount })
        assertEquals(lkr(150), series.slots.last().cumulative.amount)
        assertEquals(lkr(150).minorUnits, series.maximum)
    }

    @Test
    fun emptyAndAllZeroMonthsKeepEverySlotWithoutDividingByZero() {
        for (month in listOf(YearMonth.of(2026, 2), YearMonth.of(2028, 2), YearMonth.of(2026, 4), YearMonth.of(2026, 3))) {
            val series = month.series(daily(month), today = month.atEndOfMonth())
            assertEquals(month.lengthOfMonth(), series.slots.size)
            assertTrue(series.isAllZero)
            assertTrue(series.slots.all { it.isZeroMarker && !it.isFuture })
            assertEquals(0f, series.fractionOf(series.slots.first()), 0f)
        }
        assertEquals(28, YearMonth.of(2026, 2).lengthOfMonth())
        assertEquals(29, YearMonth.of(2028, 2).lengthOfMonth())
        assertEquals(30, YearMonth.of(2026, 4).lengthOfMonth())
        assertEquals(31, YearMonth.of(2026, 3).lengthOfMonth())
    }

    @Test
    fun missingDailyDataIsTreatedAsNoIncomeNotAShorterChart() {
        val series = october.series(emptyList())
        assertEquals(31, series.slots.size)
        assertTrue(series.isAllZero)
    }

    @Test
    fun futureSlotsAreBaselinePlaceholdersAfterToday() {
        val today = october.atDay(10)
        val series = october.series(daily(october, 2 to 400L, 20 to 999L), today)
        assertTrue(series.slots.take(10).none { it.isFuture })
        assertTrue(series.slots.drop(10).all { it.isFuture && it.cumulative.amount.isZero && !it.isZeroMarker })
        assertEquals(lkr(400).minorUnits, series.maximum)
        assertTrue(series.slots[0].isZeroMarker)
        assertFalse(series.slots[1].isZeroMarker)
    }

    @Test
    fun todayItselfIsElapsedAndTheLastDayOfAPastMonthIsNotFuture() {
        val series = october.series(daily(october, 31 to 70L), today = october.atEndOfMonth())
        assertFalse(series.slots.last().isFuture)
        assertEquals(lkr(70), series.slots.last().cumulative.amount)
    }

    @Test
    fun nextMonthRestartsAtZeroInsteadOfCarryingOver() {
        val november = YearMonth.of(2026, 11)
        val series = november.series(daily(november, 2 to 30L), today = november.atEndOfMonth())
        assertTrue(series.slots[0].isZeroMarker)
        assertEquals(lkr(30), series.slots[1].cumulative.amount)
        assertEquals(30, series.slots.size)
    }

    @Test
    fun unresolvedRatesStayFlaggedAndAreNeverCertifiedAsZero() {
        val days = daily(october, 1 to 100L).toMutableList()
        days[2] = ReportingTotal(lkr(0), unresolvedCount = 1)
        val series = october.series(days)
        assertTrue(series.hasUnresolved)
        assertTrue(series.slots[1].cumulative.isComplete)
        assertEquals(1, series.slots[2].cumulative.unresolvedCount)
        assertEquals(1, series.slots.last().cumulative.unresolvedCount)
        assertEquals(lkr(100), series.slots.last().cumulative.amount)
    }

    @Test
    fun editedOrVoidedIncomeRecomputesTheWholeSuffix() {
        val before = october.series(daily(october, 1 to 100L, 3 to 50L))
        val after = october.series(daily(october, 3 to 50L))
        assertEquals(lkr(150), before.slots[3].cumulative.amount)
        assertEquals(lkr(50), after.slots[3].cumulative.amount)
        assertTrue(after.slots[0].isZeroMarker && after.slots[1].isZeroMarker)
        assertEquals(lkr(50), after.slots.last().cumulative.amount)
    }

    @Test
    fun hugeTotalsSaturateInsteadOfOverflowing() {
        val minor = { units: Long -> ReportingTotal(FiatMoney(units, Currencies.REPORTING)) }
        val days = List(october.lengthOfMonth()) { ReportingTotal.zero() }.toMutableList()
        days[0] = minor(Long.MAX_VALUE - 5)
        days[1] = minor(100)
        days[2] = minor(100)
        val series = october.series(days)
        assertEquals(Long.MAX_VALUE - 5, series.slots[0].cumulative.amount.minorUnits)
        assertEquals(Long.MAX_VALUE, series.slots[1].cumulative.amount.minorUnits)
        assertEquals(Long.MAX_VALUE, series.slots.last().cumulative.amount.minorUnits)
        assertEquals(1f, series.fractionOf(series.slots.last()), 0f)
    }

    @Test
    fun fractionsAreRelativeToTheTallestBar() {
        val series = october.series(daily(october, 1 to 100L, 2 to 100L))
        assertEquals(0.5f, series.fractionOf(series.slots[0]), 0.0001f)
        assertEquals(1f, series.fractionOf(series.slots[1]), 0.0001f)
    }
}
