package com.example.zar.core.domain.calculation

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.correction
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.void
import com.example.zar.core.domain.model.EvidenceId
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerProjectorTest {

    private val lunch = tx("lunch", TransactionType.EXPENSE, lkr(1_000)).copy(note = "lunch", merchant = "Cafe")

    @Test
    fun appliesCorrectionsOldestFirstAndKeepsTheOriginal() {
        val entries = LedgerProjector.project(
            transactions = listOf(lunch),
            corrections = listOf(
                correction("c2", "lunch", TransactionChanges(amount = lkr(1_500)), at = T0.plusSeconds(200)),
                correction("c1", "lunch", TransactionChanges(amount = lkr(1_200), note = FieldUpdate.Clear), at = T0.plusSeconds(100)),
            ),
            voids = emptyList(),
        )
        val entry = entries.single()
        assertEquals(lkr(1_500), entry.current.amount)
        assertNull(entry.current.note)
        assertEquals(lkr(1_000), entry.original.amount)
        assertEquals(listOf("c1", "c2"), entry.corrections.map { it.id.value })
    }

    @Test
    fun replayedRecordsAreCountedOnce() {
        val c = correction("c1", "lunch", TransactionChanges(spendingClass = SpendingClass.COMMITTED))
        val entries = LedgerProjector.project(listOf(lunch, lunch), listOf(c, c), emptyList())
        assertEquals(1, entries.size)
        assertEquals(1, entries.single().corrections.size)
    }

    @Test
    fun voidCannotBeUndoneByLaterCorrectionsOrReplays() {
        val entries = LedgerProjector.project(
            transactions = listOf(lunch, lunch),
            corrections = listOf(correction("late", "lunch", TransactionChanges(amount = lkr(900)), at = T0.plusSeconds(999))),
            voids = listOf(void("lunch")),
        )
        assertTrue(entries.single().isVoided)
        assertTrue(entries.active().isEmpty())
    }

    @Test
    fun invalidCorrectionIsIgnored() {
        // Clearing the spending class of an expense would break the record.
        val bad = correction("bad", "lunch", TransactionChanges(amount = lkr(0)))
        val entry = LedgerProjector.project(listOf(lunch), listOf(bad), emptyList()).single()
        assertEquals(lkr(1_000), entry.current.amount)
        assertTrue(entry.corrections.isEmpty())
    }

    @Test
    fun evidenceLinksToOneTransactionOnly() {
        val other = tx("other", TransactionType.EXPENSE)
        val sms = EvidenceId("sms-1")
        val entries = LedgerProjector.project(
            transactions = listOf(lunch, other),
            corrections = emptyList(),
            voids = emptyList(),
            evidenceLinks = listOf(
                EvidenceLink(sms, TransactionId("other"), T0.plusSeconds(20)),
                EvidenceLink(sms, TransactionId("lunch"), T0.plusSeconds(10)),
            ),
        ).associateBy { it.id.value }
        assertEquals(listOf(sms), entries.getValue("lunch").evidenceIds)
        assertTrue(entries.getValue("other").evidenceIds.isEmpty())
    }
}
