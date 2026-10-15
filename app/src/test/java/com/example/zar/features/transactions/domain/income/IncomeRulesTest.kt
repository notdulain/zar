package com.example.zar.features.transactions.domain.income

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.transactions.domain.income.model.IncomeCategory
import com.example.zar.features.transactions.domain.income.model.IncomeDetails
import com.example.zar.features.transactions.domain.income.model.IncomeFormInput
import com.example.zar.features.transactions.domain.income.model.IncomeFormResult
import com.example.zar.features.transactions.domain.income.model.IncomeProblem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeRulesTest {

    private val today = LocalDate.of(2026, 10, 14)

    private fun input(
        amount: String = "125,000",
        currency: com.example.zar.core.domain.model.money.Currency = Currencies.LKR,
        date: LocalDate = today,
        rate: String = "",
        receivedFrom: String = "  Client payment ",
        category: IncomeCategory? = IncomeCategory.FREELANCE,
        note: String = "",
    ) = IncomeFormInput(amount, currency, date, rate, receivedFrom, category, note)

    private fun valid(input: IncomeFormInput): IncomeDetails =
        (IncomeRules.validate(input, today) as IncomeFormResult.Valid).details

    private fun problems(input: IncomeFormInput): Set<IncomeProblem> =
        (IncomeRules.validate(input, today) as IncomeFormResult.Invalid).problems

    @Test
    fun validLkrIncomeIsTrimmedAndHasNoRate() {
        val details = valid(input(rate = "300"))
        assertEquals(lkr(125_000), details.amount)
        assertEquals("Client payment", details.receivedFrom)
        assertNull("A rate is meaningless for LKR income", details.rateToReporting)
        assertNull(details.note)
    }

    @Test
    fun rejectsMissingZeroAndMalformedAmounts() {
        assertEquals(setOf(IncomeProblem.AMOUNT_MISSING), problems(input(amount = " ")))
        assertEquals(setOf(IncomeProblem.AMOUNT_INVALID), problems(input(amount = "0")))
        assertEquals(setOf(IncomeProblem.AMOUNT_INVALID), problems(input(amount = "-5")))
        assertEquals(setOf(IncomeProblem.AMOUNT_INVALID), problems(input(amount = "12.345")))
        assertEquals(setOf(IncomeProblem.AMOUNT_INVALID), problems(input(amount = "abc")))
    }

    @Test
    fun rejectsFutureDates() {
        assertEquals(setOf(IncomeProblem.DATE_IN_FUTURE), problems(input(date = today.plusDays(1))))
    }

    @Test
    fun foreignIncomeRateIsOptionalButMustBePositive() {
        assertNull(valid(input(amount = "400", currency = Currencies.USD)).rateToReporting)
        assertEquals(0, "300.5".toBigDecimal().compareTo(valid(input(amount = "400", currency = Currencies.USD, rate = "300.50")).rateToReporting))
        assertEquals(setOf(IncomeProblem.RATE_INVALID), problems(input(amount = "400", currency = Currencies.USD, rate = "0")))
        assertEquals(setOf(IncomeProblem.RATE_INVALID), problems(input(amount = "400", currency = Currencies.USD, rate = "x")))
    }

    @Test
    fun rejectsTextLongerThanFirestoreAllows() {
        assertEquals(
            setOf(IncomeProblem.RECEIVED_FROM_TOO_LONG, IncomeProblem.NOTE_TOO_LONG),
            problems(input(receivedFrom = "a".repeat(121), note = "b".repeat(501))),
        )
    }

    @Test
    fun newIncomeIsAnIncomeTransactionWithItsConversionFixedAtSaveTime() {
        val lkrIncome = IncomeRules.newIncome(TransactionId("i1"), bank, valid(input()), T0)
        assertEquals(TransactionType.INCOME, lkrIncome.type)
        assertEquals(ReportingConversion.SameCurrency, lkrIncome.reportingConversion)
        assertEquals("freelance", lkrIncome.categoryId)
        assertEquals("Client payment", lkrIncome.merchant)

        val usdKnown = IncomeRules.newIncome(TransactionId("i2"), payoneer, valid(input(amount = "400", currency = Currencies.USD, rate = "300")), T0)
        assertEquals(lkr(120_000), (usdKnown.reportingConversion as ReportingConversion.Converted).amount)

        val usdUnknown = IncomeRules.newIncome(TransactionId("i3"), payoneer, valid(input(amount = "400", currency = Currencies.USD)), T0)
        assertEquals(ReportingConversion.Missing(Currencies.LKR), usdUnknown.reportingConversion)
    }

    @Test
    fun changesContainOnlyEditedFields() {
        val original = IncomeRules.newIncome(TransactionId("i1"), bank, valid(input(note = "first")), T0)
        val edited = valid(input(amount = "130,000", receivedFrom = "Client payment", note = ""))
        val changes = IncomeRules.changesBetween(original, edited, T0)

        assertEquals(lkr(130_000), changes.amount)
        assertNull("Same rate, so the use case reuses the historical one", changes.reportingConversion)
        assertNull(changes.date)
        assertNull(changes.merchant)
        assertNull(changes.categoryId)
        assertEquals(FieldUpdate.Clear, changes.note)
        assertTrue(IncomeRules.changesBetween(original, valid(input(note = "first")), T0).isEmpty)
    }

    @Test
    fun addingAMissingRateReplacesTheConversion() {
        val original = IncomeRules.newIncome(TransactionId("i1"), payoneer, valid(input(amount = "400", currency = Currencies.USD)), T0)
        val changes = IncomeRules.changesBetween(original, valid(input(amount = "400", currency = Currencies.USD, rate = "300")), T0)

        assertNull(changes.amount)
        assertEquals(lkr(120_000), (changes.reportingConversion as ReportingConversion.Converted).amount)
    }

    @Test
    fun sameRateWrittenDifferentlyIsNotAChange() {
        val original = IncomeRules.newIncome(TransactionId("i1"), payoneer, valid(input(amount = "400", currency = Currencies.USD, rate = "300")), T0)
        val changes = IncomeRules.changesBetween(original, valid(input(amount = "400", currency = Currencies.USD, rate = "300.00")), T0)
        assertTrue(changes.isEmpty)
        assertEquals(usd(400), original.amount)
    }
}
