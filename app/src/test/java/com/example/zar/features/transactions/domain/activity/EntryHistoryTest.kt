package com.example.zar.features.transactions.domain.activity

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.correction
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.transactions.domain.activity.model.CorrectedField
import com.example.zar.features.transactions.domain.activity.model.FieldValue
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryHistoryTest {
    private val original = tx("e1", TransactionType.EXPENSE, lkr(8_400), cash).copy(merchant = "Keells Super", categoryId = "groceries")

    private fun entryWith(vararg corrections: com.example.zar.core.domain.model.Correction): LedgerEntry =
        LedgerProjector.project(listOf(original), corrections.toList(), emptyList()).single()

    @Test fun timeHistoryKeepsTheOriginalAndEveryCorrection() {
        val time = java.time.LocalTime.of(18, 30)
        val entry = entryWith(
            correction("c1", "e1", TransactionChanges(time = FieldUpdate.Set(time)), T0.plusSeconds(60)),
            correction("c2", "e1", TransactionChanges(time = FieldUpdate.Clear), T0.plusSeconds(120)),
        )
        val history = EntryHistory.build(entry)
        assertEquals(FieldValue.Time(time), history[0].changes.single().after)
        assertEquals(FieldValue.Time(time), history[1].changes.single().before)
        assertEquals(FieldValue.Time(null), history[1].changes.single().after)
        assertEquals(null, entry.original.time)
        assertEquals(null, entry.current.time)
    }

    @Test
    fun anUntouchedEntryHasNoHistory() {
        assertTrue(EntryHistory.build(entryWith()).isEmpty())
    }

    @Test
    fun eachCorrectionListsOnlyTheFieldsItChanged() {
        val entry = entryWith(
            correction("c1", "e1", TransactionChanges(amount = lkr(8_900), note = FieldUpdate.Set("Weekly groceries"))),
        )

        val event = EntryHistory.build(entry).single()

        assertEquals(setOf(CorrectedField.AMOUNT, CorrectedField.NOTE), event.changes.map { it.field }.toSet())
        val amount = event.changes.first { it.field == CorrectedField.AMOUNT }
        assertEquals(FieldValue.Amount(lkr(8_400)), amount.before)
        assertEquals(FieldValue.Amount(lkr(8_900)), amount.after)
        val note = event.changes.first { it.field == CorrectedField.NOTE }
        assertEquals(FieldValue.Text(null), note.before)
        assertEquals(FieldValue.Text("Weekly groceries"), note.after)
    }

    @Test
    fun laterCorrectionsAreComparedWithTheStateBeforeThem() {
        val entry = entryWith(
            correction("c1", "e1", TransactionChanges(amount = lkr(8_900)), T0.plusSeconds(60)),
            correction("c2", "e1", TransactionChanges(amount = lkr(9_200)), T0.plusSeconds(120)),
        )

        val events = EntryHistory.build(entry)

        assertEquals(listOf("c1", "c2"), events.map { it.correction.id.value })
        assertEquals(FieldValue.Amount(lkr(8_900)), events[1].changes.single().before)
        assertEquals(FieldValue.Amount(lkr(9_200)), events[1].changes.single().after)
    }

    @Test
    fun clearingAFieldIsShownAsAChangeToNothing() {
        val entry = entryWith(correction("c1", "e1", TransactionChanges(merchant = FieldUpdate.Clear)))

        val change = EntryHistory.build(entry).single().changes.single()

        assertEquals(CorrectedField.TITLE, change.field)
        assertEquals(FieldValue.Text("Keells Super"), change.before)
        assertEquals(FieldValue.Text(null), change.after)
    }

    @Test
    fun dateCategoryAndClassChangesAreAllReported() {
        val entry = entryWith(
            correction(
                "c1", "e1",
                TransactionChanges(
                    date = LocalDate.of(2026, 10, 9),
                    categoryId = FieldUpdate.Set("bills"),
                    spendingClass = SpendingClass.COMMITTED,
                ),
            ),
        )

        val fields = EntryHistory.build(entry).single().changes.map { it.field }

        assertEquals(setOf(CorrectedField.DATE, CorrectedField.CATEGORY, CorrectedField.SPENDING_CLASS), fields.toSet())
    }

    @Test
    fun theOriginalRecordIsNeverChangedByCorrections() {
        val entry = entryWith(correction("c1", "e1", TransactionChanges(amount = lkr(9_999))))

        assertEquals(lkr(8_400), entry.original.amount)
        assertEquals(lkr(9_999), entry.current.amount)
        assertTrue(entry.isCorrected)
    }
}
