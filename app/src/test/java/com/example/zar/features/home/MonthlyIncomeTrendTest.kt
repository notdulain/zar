package com.example.zar.features.home

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.features.home.domain.model.MonthlyIncomeTrend
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyIncomeTrendTest {
    private val october = YearMonth.of(2026, 10)
    private fun total(major: Number, unresolved: Int = 0) = ReportingTotal(lkr(major.toLong()), unresolved)

    @Test
    fun windowEndsAtTheSelectedMonthAndRunsOldestFirst() {
        val trend = MonthlyIncomeTrend.from(october, List(6) { total(100) }, LocalDate.of(2027, 1, 1))
        assertEquals(
            listOf("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10"),
            trend.points.map { it.month.toString() },
        )
        assertFalse(trend.hasPartialMonth)
    }

    @Test
    fun windowCrossesYearBoundaries() {
        val trend = MonthlyIncomeTrend.from(YearMonth.of(2027, 2), List(6) { total(1) }, LocalDate.of(2027, 6, 1))
        assertEquals("2026-09", trend.points.first().month.toString())
    }

    @Test
    fun averageAndDeltaUseActualMonthlyTotals() {
        val trend = MonthlyIncomeTrend.from(october, listOf(100, 200, 300, 400, 500, 600).map { total(it) }, LocalDate.of(2026, 11, 2))
        assertEquals(lkr(350).minorUnits, trend.averageMinor)
        assertEquals(lkr(100).minorUnits, trend.deltaMinor)
        assertEquals(lkr(600).minorUnits, trend.maximumMinor)
    }

    @Test
    fun negativeDeltaWhenTheLatestMonthIsLower() {
        val trend = MonthlyIncomeTrend.from(october, listOf(0, 0, 0, 0, 500, 300).map { total(it) }, LocalDate.of(2026, 11, 2))
        assertEquals(lkr(-200).minorUnits, trend.deltaMinor)
    }

    @Test
    fun currentMonthIsFlaggedPartialAndNothingIsExtrapolated() {
        val trend = MonthlyIncomeTrend.from(october, listOf(100, 100, 100, 100, 100, 40).map { total(it) }, LocalDate.of(2026, 10, 9))
        assertTrue(trend.latest!!.isPartial)
        assertTrue(trend.hasPartialMonth)
        assertEquals(lkr(-60).minorUnits, trend.deltaMinor)
        assertEquals(lkr(40).minorUnits, trend.latest!!.received.amount.minorUnits)
    }

    @Test
    fun viewingAPastMonthHasNoPartialPoint() {
        val trend = MonthlyIncomeTrend.from(YearMonth.of(2026, 8), List(6) { total(10) }, LocalDate.of(2026, 10, 9))
        assertFalse(trend.hasPartialMonth)
    }

    @Test
    fun unresolvedMonthsAreFlaggedNotTurnedIntoZeroCertainty() {
        val trend = MonthlyIncomeTrend.from(october, listOf(total(100), total(0, unresolved = 2), total(100), total(100), total(100), total(100)), LocalDate.of(2026, 11, 2))
        assertTrue(trend.hasUnresolvedMonth)
        assertEquals(2, trend.points[1].received.unresolvedCount)
    }

    @Test
    fun emptyWindowIsSafe() {
        val trend = MonthlyIncomeTrend.from(october, emptyList(), LocalDate.of(2026, 11, 2))
        assertNull(trend.averageMinor)
        assertNull(trend.deltaMinor)
        assertEquals(1L, trend.maximumMinor)
    }

    @Test
    fun allZeroMonthsKeepAValidScale() {
        val trend = MonthlyIncomeTrend.from(october, List(6) { total(0) }, LocalDate.of(2026, 11, 2))
        assertEquals(0L, trend.averageMinor)
        assertEquals(1L, trend.maximumMinor)
        assertEquals(0L, trend.deltaMinor)
    }
}
