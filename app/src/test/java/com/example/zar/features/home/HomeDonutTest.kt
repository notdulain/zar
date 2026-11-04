package com.example.zar.features.home

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.SpendingBreakdown
import com.example.zar.features.home.domain.model.DonutStatus
import com.example.zar.features.home.domain.model.HomeDonut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDonutTest {
    private fun total(major: Long, unresolved: Int = 0) = ReportingTotal(lkr(major), unresolved)

    private fun summary(received: ReportingTotal, spent: ReportingTotal) =
        PeriodSummary(received, SpendingBreakdown(spent, ReportingTotal.zero(), ReportingTotal.zero()), 0)

    @Test
    fun partitionReconcilesWhenPlansAreKnown() {
        val donut = HomeDonut.from(summary(total(1_000), total(400)), forPlans = total(100))
        assertEquals(DonutStatus.COMPLETE, donut.status)
        assertEquals(lkr(500).minorUnits, donut.stillFreeMinor)
        assertEquals(50, donut.stillFreePercent)
        val arcs = donut.arcFractions!!
        assertEquals(listOf(0.4f, 0.1f, 0.5f), arcs)
        assertEquals(1f, arcs.sum(), 0.0001f)
    }

    @Test
    fun missingReservationHistoryIsUnavailableNotInvented() {
        val donut = HomeDonut.from(summary(total(1_000), total(400)))
        assertNull(donut.forPlans)
        assertEquals(DonutStatus.PLANS_UNAVAILABLE, donut.status)
        assertEquals(lkr(600).minorUnits, donut.stillFreeMinor)
        assertEquals(60, donut.stillFreePercent)
        assertEquals(0f, donut.arcFractions!![1], 0f)
    }

    @Test
    fun overspendingShowsNoRingAndNoFalseFullShare() {
        val spentTooMuch = HomeDonut.from(summary(total(1_000), total(1_200)))
        assertEquals(DonutStatus.OVERSPENT, spentTooMuch.status)
        assertNull(spentTooMuch.arcFractions)
        assertNull(spentTooMuch.stillFreePercent)
        assertEquals(lkr(-200).minorUnits, spentTooMuch.stillFreeMinor)

        val plansTooMuch = HomeDonut.from(summary(total(1_000), total(800)), forPlans = total(300))
        assertEquals(DonutStatus.OVERSPENT, plansTooMuch.status)
        assertNull(plansTooMuch.arcFractions)
    }

    @Test
    fun spendingWithNoIncomeIsOverspentAndNothingAtAllIsNoIncome() {
        assertEquals(DonutStatus.OVERSPENT, HomeDonut.from(summary(total(0), total(50))).status)
        val empty = HomeDonut.from(summary(total(0), total(0)))
        assertEquals(DonutStatus.NO_INCOME, empty.status)
        assertNull(empty.arcFractions)
        assertNull(empty.stillFreePercent)
    }

    @Test
    fun partialReceivedBelowSpentIsUnresolvedNotOverspent() {
        val partialIncome = HomeDonut.from(summary(total(500, unresolved = 1), total(600)))
        assertEquals(DonutStatus.UNRESOLVED_RATES, partialIncome.status)
        assertNull(partialIncome.arcFractions)
        // Known income that spending already exceeds is overspent even when a Spent amount is also partial.
        assertEquals(DonutStatus.OVERSPENT, HomeDonut.from(summary(total(500), total(600, unresolved = 1))).status)
    }

    @Test
    fun unresolvedRatesAreFlaggedAndNeverCertified() {
        val donut = HomeDonut.from(summary(total(1_000, unresolved = 1), total(100)))
        assertEquals(DonutStatus.UNRESOLVED_RATES, donut.status)
        assertNull(donut.stillFreePercent)
        assertNull(donut.arcFractions)
        assertEquals(DonutStatus.UNRESOLVED_RATES, HomeDonut.from(summary(total(0, unresolved = 2), total(0))).status)
    }

    @Test
    fun exactlySpendingAllIncomeLeavesZeroStillFree() {
        val donut = HomeDonut.from(summary(total(1_000), total(1_000)), forPlans = total(0))
        assertEquals(DonutStatus.COMPLETE, donut.status)
        assertEquals(0, donut.stillFreePercent)
        assertNotNull(donut.arcFractions)
        assertTrue(donut.arcFractions!!.last() == 0f)
    }
}
