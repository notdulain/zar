package com.example.zar.features.plans

import com.example.zar.features.plans.data.repository.PlanPreferencesMapper
import com.example.zar.features.plans.domain.model.GoalId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanPreferencesMapperTest {
    @Test fun writesTheDocumentShapeTheRulesRequire() {
        val map = PlanPreferencesMapper.toMap(GoalId("macbook-fund"), Instant.ofEpochMilli(1_790_000_000_000))
        assertEquals(setOf("id", "mainGoalId", "updatedAt"), map.keys)
        assertEquals("main", map["id"])
        assertEquals(GoalId("macbook-fund"), PlanPreferencesMapper.mainGoalId(map))
    }

    @Test fun missingOrBlankPointersReadAsNoPreference() {
        assertNull(PlanPreferencesMapper.mainGoalId(null))
        assertNull(PlanPreferencesMapper.mainGoalId(emptyMap()))
        assertNull(PlanPreferencesMapper.mainGoalId(mapOf("mainGoalId" to " ")))
        assertNull(PlanPreferencesMapper.mainGoalId(mapOf("mainGoalId" to 7)))
    }
}
