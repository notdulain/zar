package com.example.zar.features.plans

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.MainGoalResolver
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.domain.model.newGoalId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainGoalResolverTest {
    private fun goal(id: String, name: String) = SavingsGoal(GoalId(id), name, lkr(1_000))

    @Test fun anEmptyCatalogHasNoMainGoal() = assertNull(MainGoalResolver.resolve(emptyList(), GoalId("x")))

    @Test fun aValidPreferenceWins() {
        val goals = listOf(goal("macbook-fund", "MacBook"), goal("b", "Bike"))
        assertEquals(GoalId("b"), MainGoalResolver.resolve(goals, GoalId("b")))
    }

    @Test fun theLegacyGoalStaysMainWithoutAPreferenceEvenWhenAnotherPlanIsAdded() {
        val goals = listOf(goal("a", "Aardvark"), goal("macbook-fund", "MacBook"))
        assertEquals(GoalId("macbook-fund"), MainGoalResolver.resolve(goals, null))
    }

    @Test fun anUnknownPreferenceFallsBackDeterministicallyByNameThenId() {
        val goals = listOf(goal("z", "Zebra"), goal("b2", "bike"), goal("b1", "Bike"))
        assertEquals(GoalId("b1"), MainGoalResolver.resolve(goals, GoalId("gone")))
        assertEquals(MainGoalResolver.resolve(goals, null), MainGoalResolver.resolve(goals.reversed(), null))
    }

    @Test fun newGoalIdsAreOpaqueAndUnique() {
        assertNotEquals(newGoalId(), newGoalId())
        assertNotEquals("macbook-fund", newGoalId().value)
    }
}
