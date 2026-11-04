package com.example.zar.features.plans

import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.plans.domain.model.GoalAllocation
import com.example.zar.features.plans.domain.model.GoalBackingCalculator
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.domain.usecase.SetGoalAllocationUseCase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalBackingConservationTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val shared = Account(
        AccountId("shared"), "Shared", AccountType.BANK, Currencies.LKR,
        OpeningBalance.Known(lkr(10_000)), LocalDate.of(2026, 10, 1),
    )
    private fun goal(id: String, amount: Long) =
        SavingsGoal(GoalId(id), id, lkr(20_000), listOf(GoalAllocation(shared.id, lkr(amount))))
    private val a = goal("a", 3_000)
    private val b = goal("b", 2_000)

    private fun spend(amount: Long) =
        TestLedger.entry(TestLedger.tx("spent-$amount", TransactionType.EXPENSE, lkr(amount), shared))

    @Test fun reservationsWithinTheBalanceAreBackedInFullAndNotDoubleCounted() {
        val result = GoalBackingCalculator.calculateAll(listOf(a, b), listOf(shared), emptyList(), today)
        assertEquals(lkr(3_000), result.getValue(a.id).backed)
        assertEquals(lkr(2_000), result.getValue(b.id).backed)
        val total = result.values.fold(lkr(0)) { sum, p -> sum + p.backed }
        assertTrue(total <= lkr(10_000))
        assertEquals(lkr(5_000), total)
    }

    @Test fun aBalanceThatFallsBelowTheCombinedReservationsIsSharedProportionallyNeverDuplicated() {
        val result = GoalBackingCalculator.calculateAll(listOf(a, b), listOf(shared), listOf(spend(6_000)), today)
        assertEquals(lkr(2_400), result.getValue(a.id).backed)
        assertEquals(lkr(1_600), result.getValue(b.id).backed)
        assertEquals(lkr(600), result.getValue(a.id).allocationGap)
        assertEquals(lkr(400), result.getValue(b.id).allocationGap)
        assertEquals(lkr(4_000), result.values.fold(lkr(0)) { sum, p -> sum + p.backed })
    }

    @Test fun theSplitDoesNotDependOnPlanOrder() {
        val forward = GoalBackingCalculator.calculateAll(listOf(a, b), listOf(shared), listOf(spend(6_000)), today)
        val reverse = GoalBackingCalculator.calculateAll(listOf(b, a), listOf(shared), listOf(spend(6_000)), today)
        assertEquals(forward.getValue(a.id).backed, reverse.getValue(a.id).backed)
        assertEquals(forward.getValue(b.id).backed, reverse.getValue(b.id).backed)
    }

    @Test fun roundingDownNeverBacksMoreThanTheBalance() {
        val x = goal("x", 1)
        val y = goal("y", 1)
        val z = goal("z", 1)
        val tiny = shared.copy(openingBalance = OpeningBalance.Known(lkr(0)))
        val result = GoalBackingCalculator.calculateAll(listOf(x, y, z), listOf(tiny), emptyList(), today)
        assertTrue(result.values.all { it.backed == lkr(0) })
    }

    @Test fun anOverdrawnAccountBacksNothing() {
        val result = GoalBackingCalculator.calculateAll(listOf(a, b), listOf(shared), listOf(spend(12_000)), today)
        assertEquals(lkr(0), result.getValue(a.id).backed)
        assertEquals(lkr(0), result.getValue(b.id).backed)
    }

    @Test fun anUnknownBalanceStaysUnknownForEveryPlanUsingIt() {
        val unknown = shared.copy(openingBalance = OpeningBalance.Unknown)
        val result = GoalBackingCalculator.calculateAll(listOf(a, b), listOf(unknown), emptyList(), today)
        assertTrue(result.values.all { it.hasUnknownBalance && it.targetShortfall == null })
    }

    @Test fun aPlanOnAnotherAccountIsUnaffectedByTheSharedShortfall() {
        val other = shared.copy(id = AccountId("other"), name = "Other")
        val c = SavingsGoal(GoalId("c"), "c", lkr(20_000), listOf(GoalAllocation(other.id, lkr(4_000))))
        val result = GoalBackingCalculator.calculateAll(listOf(a, b, c), listOf(shared, other), listOf(spend(6_000)), today)
        assertEquals(lkr(4_000), result.getValue(c.id).backed)
    }

    @Test fun aNewReservationBeyondWhatIsLeftIsRefusedAndExistingOnesStayUnchanged() = runBlocking {
        val goals = FakeGoalRepository(listOf(a, b, SavingsGoal(GoalId("c"), "c", lkr(20_000))))
        val use = SetGoalAllocationUseCase(goals, FakePlanAccounts(listOf(shared)), TrackingPlanLedger())
        val before = goals.goals.value.toSet()

        assertTrue(use(GoalId("c"), shared.id, lkr(5_001)).isFailure)
        assertEquals(before, goals.goals.value.toSet())
        assertTrue(use(GoalId("c"), shared.id, lkr(5_000)).isSuccess)
        assertEquals(lkr(5_000), goals.goals.value.single { it.id == GoalId("c") }.allocated)
        assertEquals(lkr(3_000), goals.goals.value.single { it.id == a.id }.allocated)
    }

    @Test fun aReductionAfterBalanceFallsKeepsOtherPlansButRefusesMoreFromTheShortAccount() = runBlocking {
        val goals = FakeGoalRepository(listOf(a, b))
        val ledger = TrackingPlanLedger(listOf(spend(6_000)))
        val use = SetGoalAllocationUseCase(goals, FakePlanAccounts(listOf(shared)), ledger)

        assertTrue(use(a.id, shared.id, lkr(2_500)).isFailure)
        assertEquals(lkr(3_000), goals.goals.value.single { it.id == a.id }.allocated)
        assertEquals(0, ledger.writeCount)
    }

    @Test fun anUnknownBalanceCannotBeReservedFrom() = runBlocking {
        val goals = FakeGoalRepository(listOf(SavingsGoal(GoalId("c"), "c", lkr(20_000))))
        val unknown = shared.copy(openingBalance = OpeningBalance.Unknown)
        assertTrue(SetGoalAllocationUseCase(goals, FakePlanAccounts(listOf(unknown)), TrackingPlanLedger())(GoalId("c"), unknown.id, lkr(1)).isFailure)
        assertNull(goals.goals.value.single().allocations.firstOrNull())
    }
}
