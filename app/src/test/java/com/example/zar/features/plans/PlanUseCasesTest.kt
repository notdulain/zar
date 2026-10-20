package com.example.zar.features.plans

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.plans.domain.model.GoalAllocation
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.domain.usecase.ObservePlansOverviewUseCase
import com.example.zar.features.plans.domain.usecase.SaveGoalUseCase
import com.example.zar.features.plans.domain.usecase.SetGoalAllocationUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanUseCasesTest {

    private val savings = Account(
        AccountId("savings"), "Savings", AccountType.BANK, Currencies.LKR,
        OpeningBalance.Known(lkr(11_200)), LocalDate.of(2026, 10, 1),
    )
    private val goal = SavingsGoal(GoalId("macbook-fund"), "MacBook fund", lkr(490_000))

    @Test
    fun accountBalanceDoesNotAutomaticallyCountAsGoalProgress() = runBlocking {
        val overview = ObservePlansOverviewUseCase(
            FakeGoalRepository(listOf(goal)),
            FakePlanAccounts(listOf(savings)),
            TrackingPlanLedger(),
        )().first()

        assertEquals(lkr(11_200), overview.allocationAccounts.single().current)
        val savedGoal = requireNotNull(overview.goal)
        assertEquals(lkr(0), savedGoal.allocated)
        assertEquals(0f, savedGoal.progress)
    }

    @Test
    fun explicitAllocationUpdatesProgressWithoutWritingToLedger() = runBlocking {
        val goals = FakeGoalRepository(listOf(goal))
        val ledger = TrackingPlanLedger()
        val result = SetGoalAllocationUseCase(goals, FakePlanAccounts(listOf(savings)), ledger)(
            goal.id,
            savings.id,
            lkr(5_000),
        )

        assertTrue(result.isSuccess)
        assertEquals(lkr(5_000), goals.goals.value.single().allocated)
        assertEquals(lkr(485_000), goals.goals.value.single().remaining)
        assertEquals(0, ledger.writeCount)
        assertTrue(ledger.entries.value.isEmpty())
    }

    @Test
    fun allocationCannotExceedAvailableAccountBalance() = runBlocking {
        val goals = FakeGoalRepository(listOf(goal))
        val result = SetGoalAllocationUseCase(goals, FakePlanAccounts(listOf(savings)), TrackingPlanLedger())(
            goal.id,
            savings.id,
            lkr(11_201),
        )

        assertTrue(result.isFailure)
        assertTrue(goals.goals.value.single().allocations.isEmpty())
    }

    @Test
    fun futureDatedIncomeCannotFundAnAllocationToday() = runBlocking {
        val emptySavings = savings.copy(openingBalance = OpeningBalance.Known(lkr(0)))
        val futureIncome = TestLedger.entry(TestLedger.tx(
            "future-income", TransactionType.INCOME, lkr(10_000), emptySavings, ZarTime.today().plusDays(1),
        ))
        val goals = FakeGoalRepository(listOf(goal))
        val result = SetGoalAllocationUseCase(
            goals, FakePlanAccounts(listOf(emptySavings)), TrackingPlanLedger(listOf(futureIncome)),
        )(goal.id, emptySavings.id, lkr(1_000))
        assertTrue(result.isFailure)
        assertTrue(goals.goals.value.single().allocations.isEmpty())
    }

    @Test
    fun mismatchedLedgerEntryBlocksNewAllocation() = runBlocking {
        val wrongCurrency = TestLedger.entry(TestLedger.tx(
            "wrong-currency", TransactionType.INCOME, TestLedger.usd(1), savings,
        ))
        val goals = FakeGoalRepository(listOf(goal))
        val result = SetGoalAllocationUseCase(
            goals, FakePlanAccounts(listOf(savings)), TrackingPlanLedger(listOf(wrongCurrency)),
        )(goal.id, savings.id, lkr(1_000))
        assertTrue(result.isFailure)
        assertTrue(goals.goals.value.single().allocations.isEmpty())
    }

    @Test
    fun replacingThenClearingAnAllocationKeepsOneExplicitAmount() = runBlocking {
        val goals = FakeGoalRepository(listOf(goal.copy(allocations = listOf(GoalAllocation(savings.id, lkr(2_000))))))
        val useCase = SetGoalAllocationUseCase(goals, FakePlanAccounts(listOf(savings)), TrackingPlanLedger())

        assertTrue(useCase(goal.id, savings.id, lkr(3_000)).isSuccess)
        assertEquals(listOf(lkr(3_000)), goals.goals.value.single().allocations.map { it.amount })
        assertTrue(useCase(goal.id, savings.id, lkr(0)).isSuccess)
        assertTrue(goals.goals.value.single().allocations.isEmpty())
    }

    @Test
    fun editingTargetBelowAllocatedAmountIsRejected() = runBlocking {
        val allocated = goal.copy(allocations = listOf(GoalAllocation(savings.id, lkr(5_000))))
        val goals = FakeGoalRepository(listOf(allocated))
        val result = SaveGoalUseCase(goals)(goal.id, "MacBook fund", lkr(4_000))

        assertTrue(result.isFailure)
        assertEquals(lkr(490_000), goals.goals.value.single().target)
    }

    @Test
    fun overviewUsesLiveLedgerBalanceForBackedAmount() = runBlocking {
        val funded = goal.copy(allocations = listOf(GoalAllocation(savings.id, lkr(8_000))))
        val ledger = TrackingPlanLedger()
        val observe = ObservePlansOverviewUseCase(FakeGoalRepository(listOf(funded)), FakePlanAccounts(listOf(savings)), ledger)
        assertEquals(lkr(8_000), observe(LocalDate.of(2026, 10, 20)).first().progress?.backed)

        ledger.entries.value = listOf(TestLedger.entry(TestLedger.tx("expense", TransactionType.EXPENSE, lkr(6_000), savings)))
        val updated = observe(LocalDate.of(2026, 10, 20)).first().progress
        assertEquals(lkr(5_200), updated?.backed)
        assertEquals(lkr(2_800), updated?.allocationGap)
    }

    @Test
    fun futureOpeningFundsCannotBeAllocatedToday() = runBlocking {
        val futureAccount = savings.copy(openingDate = ZarTime.today().plusDays(1))
        val goals = FakeGoalRepository(listOf(goal))
        val accounts = FakePlanAccounts(listOf(futureAccount))
        assertTrue(SetGoalAllocationUseCase(goals, accounts, TrackingPlanLedger())(goal.id, savings.id, lkr(1_000)).isFailure)
        assertTrue(ObservePlansOverviewUseCase(goals, accounts, TrackingPlanLedger())().first().allocationAccounts.isEmpty())
    }
}
