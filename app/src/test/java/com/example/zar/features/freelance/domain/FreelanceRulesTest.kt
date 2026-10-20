package com.example.zar.features.freelance.domain

import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.features.freelance.FreelanceFixtures.TODAY
import org.junit.Assert.assertEquals
import org.junit.Test

class FreelanceRulesTest {

    private fun problems(result: Checked<*>) = (result as Checked.Invalid).problems

    @Test
    fun projectNeedsANameAndTrimsTheClient() {
        assertEquals(ProjectInput("Website", null), (FreelanceRules.checkProject(" Website ", "  ") as Checked.Valid).value)
        assertEquals(setOf(FreelanceProblem.NAME_MISSING), problems(FreelanceRules.checkProject(" ", "Acme")))
        assertEquals(
            setOf(FreelanceProblem.NAME_TOO_LONG, FreelanceProblem.CLIENT_TOO_LONG),
            problems(FreelanceRules.checkProject("a".repeat(81), "b".repeat(81))),
        )
    }

    @Test
    fun milestoneNeedsATitleAndAPositiveAmountInTheProjectCurrency() {
        val valid = FreelanceRules.checkMilestone("Design", "1,250.50", Currencies.USD, TODAY) as Checked.Valid
        assertEquals(MilestoneInput("Design", FiatMoney(125_050, Currencies.USD), TODAY), valid.value)
        assertEquals(setOf(FreelanceProblem.NAME_MISSING, FreelanceProblem.AMOUNT_MISSING), problems(FreelanceRules.checkMilestone("", "", Currencies.USD, TODAY)))
        assertEquals(setOf(FreelanceProblem.AMOUNT_INVALID), problems(FreelanceRules.checkMilestone("Design", "0", Currencies.USD, TODAY)))
        assertEquals(setOf(FreelanceProblem.AMOUNT_INVALID), problems(FreelanceRules.checkMilestone("Design", "1.234", Currencies.USD, TODAY)))
    }

    @Test
    fun overdueMilestonesCanStillBeAdded() {
        val result = FreelanceRules.checkMilestone("Old invoice", "100", Currencies.USD, TODAY.minusMonths(2))
        assertEquals(TODAY.minusMonths(2), (result as Checked.Valid).value.dueDate)
    }

    @Test
    fun linkAmountMustFitBothTheReceiptAndTheMilestone() {
        assertEquals(usd(200), (FreelanceRules.checkLink("200", available = usd(300), remaining = usd(250)) as Checked.Valid).value)
        assertEquals(setOf(FreelanceProblem.MORE_THAN_RECEIPT), problems(FreelanceRules.checkLink("400", available = usd(300), remaining = usd(500))))
        assertEquals(setOf(FreelanceProblem.MORE_THAN_REMAINING), problems(FreelanceRules.checkLink("260", available = usd(300), remaining = usd(250))))
        assertEquals(setOf(FreelanceProblem.AMOUNT_MISSING), problems(FreelanceRules.checkLink("", available = usd(300), remaining = usd(250))))
    }
}
