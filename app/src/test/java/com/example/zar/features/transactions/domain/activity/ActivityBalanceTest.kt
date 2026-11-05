package com.example.zar.features.transactions.domain.activity

import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.SpendingBreakdown
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityBalanceTest {
    private fun summary(received: Long, spent: Long, unresolved: Int = 0) = PeriodSummary(
        ReportingTotal(FiatMoney(received, Currencies.LKR), unresolved),
        SpendingBreakdown(ReportingTotal.zero(), ReportingTotal(FiatMoney(spent, Currencies.LKR)), ReportingTotal.zero()), 0,
    )

    @Test fun eightyPercentReceivedMakesEightyPercentTeal() = assertEquals(0.8f, ActivityBalance.receivedFraction(summary(80, 20)))
    @Test fun eightyPercentSpentLeavesTwentyPercentTeal() = assertEquals(0.2f, ActivityBalance.receivedFraction(summary(20, 80)))
    @Test fun equalAmountsSplitEvenly() = assertEquals(0.5f, ActivityBalance.receivedFraction(summary(50, 50)))
    @Test fun receivedOnlyFillsTheSurface() = assertEquals(1f, ActivityBalance.receivedFraction(summary(50, 0)))
    @Test fun spentOnlyHasNoTeal() = assertEquals(0f, ActivityBalance.receivedFraction(summary(0, 50)))
    @Test fun noMoneyOrLoadingHasNoClaimedProportion() {
        assertNull(ActivityBalance.receivedFraction(null))
        assertNull(ActivityBalance.receivedFraction(summary(0, 0)))
    }
    @Test fun missingConversionStillSplitsByTheShownTotals() {
        assertEquals(0.8f, ActivityBalance.receivedFraction(summary(80, 20, unresolved = 1)))
        val s = summary(80, 20)
        assertEquals(0.8f, ActivityBalance.receivedFraction(s.copy(spent = s.spent.copy(unknown = ReportingTotal(FiatMoney.zero(Currencies.LKR), 1)))))
    }
    @Test fun largeTotalsDoNotOverflowWhenCombined() = assertEquals(0.5f, ActivityBalance.receivedFraction(summary(Long.MAX_VALUE, Long.MAX_VALUE)))
}
