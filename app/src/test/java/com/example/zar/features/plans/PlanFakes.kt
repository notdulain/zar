package com.example.zar.features.plans

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.repository.GoalRepository
import com.example.zar.features.plans.domain.repository.PlanPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGoalRepository(initial: List<SavingsGoal> = emptyList()) : GoalRepository {
    val goals = MutableStateFlow(initial)
    override fun observeGoals(): Flow<List<SavingsGoal>> = goals
    override suspend fun saveGoal(goal: SavingsGoal) {
        goals.value = goals.value.filterNot { it.id == goal.id } + goal
    }
}

class FakePlanAccounts(initial: List<Account>) : AccountRepository {
    val accounts = MutableStateFlow(initial)
    override fun observeAccounts(): Flow<List<Account>> = accounts
    override suspend fun saveAccount(account: Account) {
        accounts.value = accounts.value.filterNot { it.id == account.id } + account
    }
}

class TrackingPlanLedger(initial: List<LedgerEntry> = emptyList()) : LedgerRepository {
    val entries = MutableStateFlow(initial)
    var writeCount = 0
    override fun observeLedger(): Flow<List<LedgerEntry>> = entries
    override suspend fun saveTransaction(transaction: Transaction) { writeCount++ }
    override suspend fun saveTransfer(transfer: Transfer) { writeCount++ }
    override suspend fun addCorrection(correction: Correction) { writeCount++ }
    override suspend fun void(markers: List<VoidMarker>) { writeCount++ }
    override suspend fun linkEvidence(link: EvidenceLink) { writeCount++ }
}

class FakePlanPreferences(initial: GoalId? = null) : PlanPreferencesRepository {
    val mainGoalId = MutableStateFlow(initial)
    var writeCount = 0
    override fun observeMainGoalId(): Flow<GoalId?> = mainGoalId
    override suspend fun setMainGoalId(id: GoalId) { writeCount++; mainGoalId.value = id }
}
