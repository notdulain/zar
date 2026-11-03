package com.example.zar.features.plans.domain.usecase

import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.resultOf
import com.example.zar.features.plans.domain.model.GoalAllocation
import com.example.zar.features.plans.domain.model.GoalBackingCalculator
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.MainGoalResolver
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.domain.repository.GoalRepository
import com.example.zar.features.plans.domain.repository.PlanPreferencesRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class ObservePlansOverviewUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val accounts: AccountRepository,
    private val ledger: LedgerRepository,
    private val preferences: PlanPreferencesRepository,
) {
    operator fun invoke(today: LocalDate = ZarTime.today()): Flow<PlansOverview> = combine(
        goals.observeGoals(),
        accounts.observeAccounts(),
        ledger.observeLedger(),
        // The pointer is a convenience: if it cannot be read (offline rules gap, permission), plans still load and the
        // legacy/fallback main applies. A read failure must never look like an empty or failed catalog.
        preferences.observeMainGoalId().catch { if (it is CancellationException) throw it else emit(null) },
    ) { savedGoals, savedAccounts, entries, preferredId ->
        val catalog = savedGoals.sortedWith(compareBy({ it.name.lowercase() }, { it.id.value }))
        val mainId = MainGoalResolver.resolve(catalog, preferredId)
        val accountsByGoal = catalog.associate { goal -> goal.id to eligibleAccounts(goal, savedAccounts, entries, today) }
        val progressByGoal = GoalBackingCalculator.calculateAll(catalog, savedAccounts, entries, today)
        val main = catalog.firstOrNull { it.id == mainId }
        PlansOverview(
            goal = main,
            allocationAccounts = main?.let { accountsByGoal[it.id] }.orEmpty(),
            progress = main?.let { progressByGoal[it.id] },
            goals = catalog,
            progressByGoal = progressByGoal,
            accountsByGoal = accountsByGoal,
        )
    }

    private fun eligibleAccounts(
        goal: SavingsGoal,
        savedAccounts: List<Account>,
        entries: List<LedgerEntry>,
        today: LocalDate,
    ): List<AccountBalance> = savedAccounts
        .asSequence()
        .filter { account -> !account.isArchived || goal.allocations.any { it.accountId == account.id } }
        .filter { it.currency == goal.target.currency }
        .filter { account -> account.openingDate <= today || goal.allocations.any { it.accountId == account.id } }
        .map { AccountBalanceCalculator.calculate(it, entries, today) }
        .filter { balance ->
            (balance.current != null && !balance.current.isNegative) ||
                goal.allocations.any { it.accountId == balance.account.id }
        }
        .sortedBy { it.account.name.lowercase() }
        .toList()
}

class SaveGoalUseCase @Inject constructor(
    private val goals: GoalRepository,
) {
    suspend operator fun invoke(id: GoalId, name: String, target: Money, targetDate: LocalDate? = null): Result<Unit> = resultOf {
        val existing = goals.observeGoals().first().firstOrNull { it.id == id }
        goals.saveGoal(SavingsGoal(id, name.trim(), target, existing?.allocations.orEmpty(), targetDate))
    }
}

/** Replaces one account's allocation. A zero amount removes it. No ledger write occurs here. */
class SetGoalAllocationUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val accounts: AccountRepository,
    private val ledger: LedgerRepository,
) {
    suspend operator fun invoke(goalId: GoalId, accountId: AccountId, amount: Money): Result<Unit> = resultOf {
        val savedGoals = goals.observeGoals().first()
        val goal = savedGoals.firstOrNull { it.id == goalId }
            ?: throw LedgerError.InvalidRecord("Goal not found")
        val account = accounts.observeAccounts().first().firstOrNull { it.id == accountId }
            ?: throw LedgerError.InvalidRecord("Account not found")
        if (amount.isNegative) throw LedgerError.InvalidRecord("Allocation cannot be negative")
        if (amount.currency != goal.target.currency || amount.currency != account.currency) {
            throw LedgerError.CurrencyMismatch(goal.target.currency.code, amount.currency.code)
        }

        val updatedAllocations = goal.allocations.filterNot { it.accountId == accountId }.toMutableList()
        if (amount.isPositive) {
            if (account.isArchived) throw LedgerError.AccountArchived(accountId)
            val today = ZarTime.today()
            if (account.openingDate > today) throw LedgerError.InvalidRecord("This account has not opened yet")
            val accountBalance = AccountBalanceCalculator.calculate(account, ledger.observeLedger().first(), today)
            if (accountBalance.mismatchedCount > 0) {
                throw LedgerError.InvalidRecord("Fix entries in the wrong currency before allocating")
            }
            val balance = accountBalance.current
                ?: throw LedgerError.InvalidRecord("This account has an unknown balance")
            val reservedElsewhere = savedGoals
                .asSequence()
                .filterNot { it.id == goalId }
                .flatMap { it.allocations.asSequence() }
                .filter { it.accountId == accountId }
                .fold(Money.zero(account.currency)) { total, allocation -> total + allocation.amount }
            val available = balance - reservedElsewhere
            if (amount > available) throw LedgerError.InvalidRecord("Allocation is larger than the available balance")
            updatedAllocations += GoalAllocation(accountId, amount)
        }
        goals.saveGoal(goal.copy(allocations = updatedAllocations))
    }
}

/** Makes an existing plan the main one. It only moves the pointer: no plan or allocation is written. */
class SetMainGoalUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val preferences: PlanPreferencesRepository,
) {
    suspend operator fun invoke(id: GoalId): Result<Unit> = resultOf {
        if (goals.observeGoals().first().none { it.id == id }) throw LedgerError.InvalidRecord("Goal not found")
        preferences.setMainGoalId(id)
    }
}
