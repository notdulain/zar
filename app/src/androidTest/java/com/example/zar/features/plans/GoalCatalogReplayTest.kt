package com.example.zar.features.plans

import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.auth.IsolatedFirebaseApp
import com.example.zar.features.auth.EmulatorFirebase
import com.example.zar.features.auth.step
import com.example.zar.features.plans.data.repository.FirestoreGoalRepository
import com.example.zar.features.plans.data.repository.FirestorePlanPreferencesRepository
import com.example.zar.features.plans.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Real SDK + local rules, including persisted cache replay; no production Firebase access. */
class GoalCatalogReplayTest {
    @Test fun legacyAndMultipleGoalsSurviveRetryAndOfflineClientRestart() = runBlocking {
        EmulatorFirebase.requireEmulators()
        val name = "goal-replay-${UUID.randomUUID()}"
        val email = "$name@zar.test"
        var client = IsolatedFirebaseApp.open(name)
        val users = MutableStateFlow<UserId?>(null)
        val session = object : UserSessionProvider {
            override fun currentUserId() = users.value
            override fun observeUserId() = users
        }
        fun money(minor: Long) = FiatMoney(minor, Currencies.LKR)
        val legacy = SavingsGoal(GoalId(LEGACY_GOAL_ID), "Legacy goal", money(1_000_000),
            listOf(GoalAllocation(AccountId("bank"), money(300_000))))
        val b = SavingsGoal(GoalId("goal-b"), "New phone", money(500_000),
            listOf(GoalAllocation(AccountId("bank"), money(200_000))))
        val c = SavingsGoal(GoalId("goal-c"), "Emergency fund", money(300_000))
        try {
            users.value = UserId(step("sign in") { client.signInOrCreate(email, "test-password-1") })
            var goals = FirestoreGoalRepository(client.db, session)
            var prefs = FirestorePlanPreferencesRepository(client.db, session)
            goals.saveGoal(legacy); goals.saveGoal(b); prefs.setMainGoalId(b.id)
            goals.saveGoal(c); goals.saveGoal(c) // retry must reuse the same document
            step("acknowledge writes") { client.db.waitForPendingWrites().await() }
            assertEquals(setOf(legacy, b, c), step("catalog") { goals.observeGoals().first { it.size == 3 } }.toSet())
            assertEquals(b.id, step("main") { prefs.observeMainGoalId().first { it == b.id } })
            client.close()
            client = IsolatedFirebaseApp.open(name)
            client.db.disableNetwork().await()
            goals = FirestoreGoalRepository(client.db, session)
            prefs = FirestorePlanPreferencesRepository(client.db, session)
            val restored = step("offline persisted catalog") { goals.observeGoals().first { it.size == 3 } }
            assertEquals(setOf(legacy, b, c), restored.toSet())
            assertEquals(500_000L, restored.sumOf { (it.allocated as FiatMoney).minorUnits })
            assertEquals(b.id, step("offline main") { prefs.observeMainGoalId().first { it == b.id } })
            users.value = null
            assertTrue(step("signed out isolation") { goals.observeGoals().first() }.isEmpty())
            assertNull(step("signed out main") { prefs.observeMainGoalId().first() })
        } finally { client.close() }
    }
}
