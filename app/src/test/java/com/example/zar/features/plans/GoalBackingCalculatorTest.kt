package com.example.zar.features.plans

import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.plans.domain.model.GoalAllocation
import com.example.zar.features.plans.domain.model.GoalBackingCalculator
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.SavingsGoal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalBackingCalculatorTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val account = Account(
        AccountId("savings"), "Savings", AccountType.BANK, Currencies.LKR,
        OpeningBalance.Known(lkr(10_000)), LocalDate.of(2026, 10, 1),
    )
    private val goal = SavingsGoal(
        GoalId("macbook-fund"), "MacBook fund", lkr(20_000),
        listOf(GoalAllocation(account.id, lkr(8_000))), today.plusDays(14),
    )

    @Test fun backingIsCappedByAllocationAndRecordedBalance() {
        val spending = TestLedger.entry(TestLedger.tx("spent", TransactionType.EXPENSE, lkr(6_000), account))
        val result = GoalBackingCalculator.calculate(goal, listOf(account), listOf(spending), today)
        assertEquals(lkr(4_000), result.backed)
        assertEquals(lkr(4_000), result.allocationGap)
        assertEquals(lkr(16_000), result.targetShortfall)
        assertEquals(lkr(8_000), result.weeklyPace)
        assertEquals(TestLedger.OCT_10, result.balanceAsOf)
    }

    @Test fun negativeBalanceContributesZeroBacking() {
        val spending = TestLedger.entry(TestLedger.tx("overdrawn", TransactionType.EXPENSE, lkr(12_000), account))
        val result = GoalBackingCalculator.calculate(goal, listOf(account), listOf(spending), today)
        assertEquals(lkr(0), result.backed)
        assertEquals(lkr(8_000), result.allocationGap)
        assertEquals(lkr(20_000), result.targetShortfall)
    }

    @Test fun correctedSpendingAndAccountChangesRecalculateBacking() {
        val original = TestLedger.tx("spent", TransactionType.EXPENSE, lkr(2_000), account)
        val first = GoalBackingCalculator.calculate(goal, listOf(account), listOf(TestLedger.entry(original)), today)
        val correction = TestLedger.correction("corrected", "spent", TransactionChanges(amount = lkr(6_000), reportingConversion = CurrencyConverter.toReporting(lkr(6_000), null)))
        val corrected = LedgerEntry(original, original.copy(amount = lkr(6_000)), listOf(correction), null, emptyList())
        val second = GoalBackingCalculator.calculate(goal, listOf(account), listOf(corrected), today)
        val changedAccount = account.copy(openingBalance = OpeningBalance.Known(lkr(7_000)))
        val third = GoalBackingCalculator.calculate(goal, listOf(changedAccount), listOf(corrected), today)
        assertEquals(lkr(8_000), first.backed)
        assertEquals(lkr(4_000), second.backed)
        assertEquals(lkr(1_000), third.backed)
    }

    @Test fun futureIncomeIsNotAssumedToHaveArrived() {
        val future = TestLedger.entry(TestLedger.tx("future", TransactionType.INCOME, lkr(100_000), account, today.plusDays(1)))
        val result = GoalBackingCalculator.calculate(goal, listOf(account.copy(openingBalance = OpeningBalance.Known(lkr(0)))), listOf(future), today)
        assertEquals(lkr(0), result.backed)
        assertEquals(lkr(20_000), result.targetShortfall)
    }

    @Test fun unknownOrMismatchedBalanceDoesNotClaimAccurateShortfallOrPace() {
        val unknown = GoalBackingCalculator.calculate(goal, listOf(account.copy(openingBalance = OpeningBalance.Unknown)), emptyList(), today)
        assertTrue(unknown.hasUnknownBalance)
        assertEquals(lkr(0), unknown.backed)
        assertNull(unknown.allocationGap)
        assertNull(unknown.targetShortfall)
        assertNull(unknown.weeklyPace)

        val wrongCurrency = TestLedger.entry(TestLedger.tx("wrong", TransactionType.INCOME, TestLedger.usd(1), account))
        val mismatched = GoalBackingCalculator.calculate(goal, listOf(account), listOf(wrongCurrency), today)
        assertTrue(mismatched.hasMismatchedEntries)
        assertTrue(mismatched.hasUnknownBalance)
        assertNull(mismatched.targetShortfall)
    }

    @Test fun approximateAndPastDueStatesAreExplicit() {
        val estimate = GoalBackingCalculator.calculate(
            goal.copy(targetDate = today.plusDays(10)),
            listOf(account.copy(openingBalance = OpeningBalance.Approximate(lkr(5_000)))), emptyList(), today,
        )
        assertTrue(estimate.isApproximate)
        assertFalse(estimate.hasUnknownBalance)
        assertEquals(lkr(10_500), estimate.weeklyPace)

        val past = GoalBackingCalculator.calculate(goal.copy(targetDate = today.minusDays(1)), listOf(account), emptyList(), today)
        assertEquals(-1L, past.daysRemaining)
        assertNull(past.weeklyPace)
    }

    @Test fun futureOpeningFundsCannotBackAGoalToday() {
        val result = GoalBackingCalculator.calculate(goal, listOf(account.copy(openingDate = today.plusDays(1))), emptyList(), today)
        assertEquals(lkr(0), result.backed)
        assertTrue(result.hasUnknownBalance)
        assertNull(result.weeklyPace)
        assertNull(result.balanceAsOf)
    }

    @Test fun fullyBackedGoalHasNoShortfallOrPace() {
        val funded = goal.copy(target = lkr(8_000))
        val result = GoalBackingCalculator.calculate(funded, listOf(account), emptyList(), today)
        assertEquals(lkr(0), result.targetShortfall)
        assertEquals(1f, result.backedFraction(funded.target))
        assertNull(result.weeklyPace)
    }

    @Test fun weeklyPaceRoundsUpToTheSmallestCurrencyUnit() {
        val tinyGoal = SavingsGoal(goal.id, goal.name, FiatMoney(1, Currencies.LKR), targetDate = today.plusDays(3))
        val result = GoalBackingCalculator.calculate(tinyGoal, emptyList(), emptyList(), today)
        assertEquals(FiatMoney(3, Currencies.LKR), result.weeklyPace)
    }
}
