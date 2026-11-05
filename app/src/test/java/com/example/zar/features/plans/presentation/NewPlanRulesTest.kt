package com.example.zar.features.plans.presentation

import com.example.zar.features.plans.presentation.screen.PlansHeroSpec
import com.example.zar.features.plans.presentation.screen.monthsToReach
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NewPlanRulesTest {
    @Test
    fun monthsToReachIsAtLeastOne() {
        val today = LocalDate.of(2026, 10, 27)
        assertEquals(1L, monthsToReach(today, today.plusDays(3)))
        assertEquals(6L, monthsToReach(today, today.plusMonths(6)))
        assertEquals(1L, monthsToReach(today, today.minusDays(5)))
    }

    @Test
    fun theHeroGradientIsFigmaZ04() {
        // Figma 160:2421: from y -9.82 to y 481 in a 420dp hero, stops 0 / .10 / .70 / 1.
        assertEquals(-9.82f, PlansHeroSpec.GRADIENT_START_DP, 0f)
        assertEquals(481f, PlansHeroSpec.GRADIENT_END_DP, 0f)
        assertEquals(listOf(0f, 0.10f, 0.4943f, 1f), PlansHeroSpec.StopFractions.toList())
        assertEquals(420f, PlansHeroSpec.Height.value, 0f)
    }
}
