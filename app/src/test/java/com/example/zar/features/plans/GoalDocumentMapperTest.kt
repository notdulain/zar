package com.example.zar.features.plans

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.model.AccountId
import com.example.zar.features.plans.data.repository.GoalDocumentMapper
import com.example.zar.features.plans.domain.model.GoalAllocation
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.SavingsGoal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalDocumentMapperTest {

    @Test
    fun roundTripsGoalAndAllocations() {
        val goal = SavingsGoal(
            GoalId("macbook-fund"), "MacBook fund", lkr(490_000),
            listOf(GoalAllocation(AccountId("savings"), lkr(11_200))),
            LocalDate.of(2027, 6, 1),
        )
        assertEquals(goal, GoalDocumentMapper.fromMap(GoalDocumentMapper.toMap(goal, Instant.ofEpochMilli(1))))
    }

    @Test fun oldGoalWithoutDateStillLoads() {
        val goal = SavingsGoal(GoalId("macbook-fund"), "MacBook fund", lkr(490_000))
        val saved = GoalDocumentMapper.toMap(goal, Instant.ofEpochMilli(1))
        assertEquals(goal, GoalDocumentMapper.fromMap(saved))
        assertEquals(false, saved.containsKey("targetDate"))
    }

    @Test fun invalidTargetDateDoesNotDisappearSilently() {
        val goal = SavingsGoal(GoalId("macbook-fund"), "MacBook fund", lkr(490_000))
        val saved = GoalDocumentMapper.toMap(goal, Instant.ofEpochMilli(1))
        assertNull(GoalDocumentMapper.fromMap(saved + ("targetDate" to "invalid")))
    }

    @Test
    fun malformedGoalIsSkipped() {
        assertNull(GoalDocumentMapper.fromMap(mapOf("id" to "broken")))
    }
    @Test
    fun missingOrInvalidAllocationsAreNotSilentlyTreatedAsEmpty() {
        val goal = SavingsGoal(GoalId("macbook-fund"), "MacBook fund", lkr(490_000))
        val map = GoalDocumentMapper.toMap(goal, Instant.ofEpochMilli(1))
        assertNull(GoalDocumentMapper.fromMap(map - "allocations"))
        assertNull(GoalDocumentMapper.fromMap(map + ("allocations" to "invalid")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun allocationLimitMatchesFirestoreRules() {
        SavingsGoal(GoalId("macbook-fund"), "MacBook fund", lkr(490_000),
            List(11) { GoalAllocation(AccountId("account-$it"), lkr(100)) })
    }

}
