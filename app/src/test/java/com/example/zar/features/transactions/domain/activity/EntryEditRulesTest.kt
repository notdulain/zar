package com.example.zar.features.transactions.domain.activity

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryEditRulesTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val expense: LedgerEntry = entry(
        tx("e1", TransactionType.EXPENSE, lkr(8_400), cash, LocalDate.of(2026, 10, 24), SpendingClass.DISCRETIONARY)
            .let { it.copy(merchant = "Keells Super", note = "Weekly groceries", categoryId = "groceries") },
    )
    private val start = ExpenseEditInput.from(expense.current)

    private fun problems(input: ExpenseEditInput, entry: LedgerEntry = expense) =
        (EntryEditRules.validate(entry, input, today) as EditResult.Invalid).problems

    private fun changes(input: ExpenseEditInput, entry: LedgerEntry = expense) =
        (EntryEditRules.validate(entry, input, today) as EditResult.Valid).changes

    @Test fun timeCanBeAddedChangedAndCleared() {
        val time = java.time.LocalTime.of(18, 30)
        assertEquals(FieldUpdate.Set(time), changes(start.copy(time = time)).time)
        val timed = expense.copy(current = expense.current.copy(time = time))
        val input = ExpenseEditInput.from(timed.current)
        assertEquals(time, input.time)
        assertEquals(FieldUpdate.Clear, changes(input.copy(time = null), timed).time)
        assertEquals(setOf(EditProblem.NOTHING_CHANGED), problems(input, timed))
    }

    @Test
    fun theFormStartsFromTheEntrysCurrentValues() {
        assertEquals("8400", start.amount)
        assertEquals("groceries", start.categoryId)
        assertEquals("Keells Super", start.title)
        assertEquals("Weekly groceries", start.description)
        assertEquals(SpendingClass.DISCRETIONARY, start.spendingClass)
    }

    @Test
    fun savingWithoutChangingAnythingIsRefused() {
        assertEquals(setOf(EditProblem.NOTHING_CHANGED), problems(start))
    }

    @Test
    fun onlyTheChangedFieldsAreIncluded() {
        val result = changes(start.copy(amount = "8,900"))

        assertEquals(lkr(8_900), result.amount)
        assertNull(result.date)
        assertNull(result.categoryId)
        assertNull(result.merchant)
        assertNull(result.note)
        assertNull(result.spendingClass)
        // The stored historical rate is reused later by CorrectTransactionUseCase, so none is set here.
        assertNull(result.reportingConversion)
    }

    @Test
    fun aDecimalAmountIsAccepted() {
        assertEquals(lkr(0).let { com.example.zar.core.domain.model.money.FiatMoney(850_50, it.currency) }, changes(start.copy(amount = "850.50")).amount)
    }

    @Test
    fun aMissingAmountIsReported() {
        assertEquals(setOf(EditProblem.AMOUNT_MISSING), problems(start.copy(amount = "  ")))
    }

    @Test
    fun anInvalidAmountIsReported() {
        assertEquals(setOf(EditProblem.AMOUNT_INVALID), problems(start.copy(amount = "abc")))
        assertEquals(setOf(EditProblem.AMOUNT_INVALID), problems(start.copy(amount = "0")))
        assertEquals(setOf(EditProblem.AMOUNT_INVALID), problems(start.copy(amount = "-5")))
        assertEquals(setOf(EditProblem.AMOUNT_INVALID), problems(start.copy(amount = "10.123")))
    }

    @Test
    fun theAmountIsReadInTheEntrysOwnCurrency() {
        val usdEntry = entry(tx("u1", TransactionType.EXPENSE, usd(40), payoneer, LocalDate.of(2026, 10, 20)))

        val result = changes(ExpenseEditInput.from(usdEntry.current).copy(amount = "45"), usdEntry)

        assertEquals(usd(45), result.amount)
    }

    @Test
    fun aFutureDateIsRefusedButTodayIsFine() {
        assertEquals(setOf(EditProblem.DATE_IN_FUTURE), problems(start.copy(date = today.plusDays(1))))
        assertEquals(today.minusDays(1), changes(start.copy(date = today.minusDays(1))).date)
    }

    @Test
    fun overlongTextIsRefused() {
        assertEquals(setOf(EditProblem.TITLE_TOO_LONG), problems(start.copy(title = "x".repeat(121))))
        assertEquals(setOf(EditProblem.DESCRIPTION_TOO_LONG), problems(start.copy(description = "x".repeat(501))))
        assertEquals("x".repeat(120), (changes(start.copy(title = "x".repeat(120))).merchant as FieldUpdate.Set).value)
    }

    @Test
    fun blankTextClearsTheFieldAndSurroundingSpacesAreTrimmed() {
        val cleared = changes(start.copy(description = "   "))
        assertEquals(FieldUpdate.Clear, cleared.note)

        val trimmed = changes(start.copy(title = "  Keells  "))
        assertEquals(FieldUpdate.Set("Keells"), trimmed.merchant)
    }

    @Test
    fun anEntryWithoutACategoryCanStayThatWay() {
        val imported = entry(tx("i1", TransactionType.EXPENSE, lkr(5_850), cash, LocalDate.of(2026, 10, 22), SpendingClass.UNKNOWN))
        val form = ExpenseEditInput.from(imported.current)

        assertNull(form.categoryId)
        assertNull(changes(form.copy(amount = "5900"), imported).categoryId)
        assertEquals(FieldUpdate.Set("bills"), changes(form.copy(categoryId = "bills"), imported).categoryId)
    }

    @Test
    fun changingTheCountsAsClassIsACorrection() {
        val result = changes(start.copy(spendingClass = SpendingClass.COMMITTED))

        assertEquals(SpendingClass.COMMITTED, result.spendingClass)
        assertTrue(result.amount == null && result.date == null)
    }

    @Test
    fun aCorrectedEntryIsComparedWithItsCurrentValuesNotTheOriginal() {
        val corrected = LedgerEntryFixtures.corrected(expense, amount = 8_900)

        // 8,900 is now the current amount, so typing it again is not a change.
        assertEquals(setOf(EditProblem.NOTHING_CHANGED), problems(ExpenseEditInput.from(corrected.current), corrected))
        assertEquals(lkr(8_400), changes(ExpenseEditInput.from(corrected.current).copy(amount = "8400"), corrected).amount)
        assertTrue(T0 < T0.plusSeconds(1))
    }
}

private object LedgerEntryFixtures {
    fun corrected(entry: LedgerEntry, amount: Long): LedgerEntry {
        val correction = com.example.zar.core.domain.TestLedger.correction(
            "c1", entry.id.value, com.example.zar.core.domain.model.TransactionChanges(amount = lkr(amount)),
        )
        return com.example.zar.core.domain.calculation.LedgerProjector
            .project(listOf(entry.original), listOf(correction), emptyList()).single()
    }
}
