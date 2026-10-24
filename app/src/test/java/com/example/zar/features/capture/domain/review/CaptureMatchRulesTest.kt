package com.example.zar.features.capture.domain.review

import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.EvidenceId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureMatchRulesTest {
    private fun draft(
        id: String = "d1",
        amount: Money? = lkr(800),
        direction: Direction? = Direction.OUT,
        merchant: String? = "CAFE SEVENTY THREE MALABE LK",
        date: java.time.LocalDate? = OCT_10,
        status: DraftStatus = DraftStatus.PENDING_REVIEW,
    ) = Draft(
        DraftId(id), DraftSource.SMS,
        listOf(DraftProposal.ActualTransaction(null, amount, null, date, merchant = merchant, uncertainFields = setOf(DraftField.TYPE), direction = direction)),
        status, T0,
    )

    private fun spend(id: String, amount: Money = lkr(800), merchant: String? = "Cafe Seventy Three", account: com.example.zar.core.domain.model.Account = bank, days: Long = 0) =
        entry(tx(id, TransactionType.EXPENSE, amount, account, OCT_10.plusDays(days)).copy(merchant = merchant))

    // --- Possible duplicates ---------------------------------------------------------------

    @Test
    fun anEntryWithTheSameAmountDayAndNameIsALikelyDuplicate() {
        val match = CaptureMatchRules.possibleDuplicates(draft(), null, listOf(spend("a"))).single()

        assertEquals("a", match.entry.id.value)
        assertEquals(MatchStrength.LIKELY, match.strength)
    }

    @Test
    fun theSameAccountMakesItLikelyEvenWhenTheNamesDiffer() {
        val entries = listOf(spend("a", merchant = "Lunch"))

        assertEquals(MatchStrength.LIKELY, CaptureMatchRules.possibleDuplicates(draft(), bank.id, entries).single().strength)
        assertEquals(MatchStrength.POSSIBLE, CaptureMatchRules.possibleDuplicates(draft(), cash.id, entries).single().strength)
        assertEquals(MatchStrength.POSSIBLE, CaptureMatchRules.possibleDuplicates(draft(), null, entries).single().strength)
    }

    @Test
    fun aShopNameIsMatchedByItsDistinctiveWordsNotByCommonOnes() {
        assertTrue(CaptureMatchRules.sharesName("CAFE SEVENTY THREE MALABE LK", "Cafe Seventy Three"))
        assertTrue(CaptureMatchRules.sharesName("KEELLS SUPER KOLLUPITIYA", "Keells"))
        assertFalse(CaptureMatchRules.sharesName("Cafe Zero", "Cafe Seventy Three"))
        assertFalse(CaptureMatchRules.sharesName(null, "Keells"))
        assertFalse(CaptureMatchRules.sharesName("", ""))
    }

    @Test
    fun entriesOutsideTheWindowAmountDirectionOrCurrencyAreNeverOffered() {
        val entries = listOf(
            spend("late", days = 3),
            spend("early", days = -3),
            spend("amount", amount = lkr(801)),
            spend("usd", amount = usd(800)),
            entry(tx("income", TransactionType.INCOME, lkr(800))),
            entry(tx("transfer-out", TransactionType.TRANSFER_OUT, lkr(800), transferId = "t1")),
            entry(tx("adjustment", TransactionType.ADJUSTMENT_OUT, lkr(800))),
        )

        assertTrue(CaptureMatchRules.possibleDuplicates(draft(), null, entries).isEmpty())
    }

    @Test
    fun theWindowIncludesTwoDaysEitherSide() {
        val entries = listOf(spend("before", days = -2), spend("after", days = 2))

        assertEquals(setOf("before", "after"), CaptureMatchRules.possibleDuplicates(draft(), null, entries).map { it.entry.id.value }.toSet())
    }

    @Test
    fun aMoneyInMessageOnlyMatchesIncomeNotSpending() {
        val entries = listOf(spend("spent"), entry(tx("paid-in", TransactionType.INCOME, lkr(800))))

        val matches = CaptureMatchRules.possibleDuplicates(draft(direction = Direction.IN), null, entries)

        assertEquals(listOf("paid-in"), matches.map { it.entry.id.value })
    }

    @Test
    fun undoneEntriesTheDraftsOwnRecordAndAlreadyLinkedEntriesAreNotOffered() {
        val voided = entry(tx("voided", TransactionType.EXPENSE, lkr(800)), voided = true)
        val own = spend("draft-d1-0")
        val linked = spend("linked").let { it.copy(evidenceIds = listOf(EvidenceId("d1"))) }
        val other = spend("other").let { it.copy(evidenceIds = listOf(EvidenceId("someone-else"))) }

        val matches = CaptureMatchRules.possibleDuplicates(draft(), null, listOf(voided, own, linked, other))

        assertEquals(listOf("other"), matches.map { it.entry.id.value })
    }

    @Test
    fun twoEqualPurchasesAreBothOfferedSoThePersonDecides() {
        val matches = CaptureMatchRules.possibleDuplicates(draft(), null, listOf(spend("a", days = 1), spend("b", days = 0)))

        assertEquals(listOf("b", "a"), matches.map { it.entry.id.value })
    }

    @Test
    fun likelyMatchesComeFirstAndNoMoreThanThreeAreShown() {
        val entries = listOf(
            spend("p1", merchant = "Lunch", account = cash), spend("l1"), spend("p2", merchant = "Lunch", account = cash),
            spend("p3", merchant = "Lunch", account = cash), spend("p4", merchant = "Lunch", account = cash),
        )

        val matches = CaptureMatchRules.possibleDuplicates(draft(), null, entries)

        assertEquals(3, matches.size)
        assertEquals("l1", matches.first().entry.id.value)
    }

    @Test
    fun withoutADirectionNothingIsMatchedUntilATypeIsChosen() {
        val old = draft(direction = null)
        val entries = listOf(spend("a"))

        assertTrue(CaptureMatchRules.possibleDuplicates(old, null, entries).isEmpty())
        assertEquals(listOf("a"), CaptureMatchRules.possibleDuplicates(old, null, entries, chosen = TransactionType.EXPENSE).map { it.entry.id.value })
    }

    @Test
    fun aDraftMissingItsAmountOrDateMatchesNothing() {
        assertTrue(CaptureMatchRules.possibleDuplicates(draft(amount = null), null, listOf(spend("a"))).isEmpty())
        assertTrue(CaptureMatchRules.possibleDuplicates(draft(date = null), null, listOf(spend("a"))).isEmpty())
    }

    // --- Transfer counterparts -------------------------------------------------------------

    private fun transferLegs(id: String, amount: Money = lkr(25_000), days: Long = 0): List<LedgerEntry> = listOf(
        entry(tx("$id-out", TransactionType.TRANSFER_OUT, amount, cash, OCT_10.plusDays(days), transferId = id)),
        entry(tx("$id-in", TransactionType.TRANSFER_IN, amount, bank, OCT_10.plusDays(days), transferId = id)),
    )

    @Test
    fun moneyInMatchesTheIncomingLegAndNamesTheLegOnTheOtherSide() {
        val credit = draft(amount = lkr(25_000), direction = Direction.IN)

        val match = CaptureMatchRules.transferCounterparts(credit, bank.id, transferLegs("t1")).single()

        assertEquals("t1-in", match.leg.id.value)
        assertEquals("t1-out", match.other?.id?.value)
    }

    @Test
    fun moneyOutMatchesTheOutgoingLeg() {
        val debit = draft(amount = lkr(25_000), direction = Direction.OUT)

        val match = CaptureMatchRules.transferCounterparts(debit, null, transferLegs("t1")).single()

        assertEquals("t1-out", match.leg.id.value)
    }

    @Test
    fun aTransferOnAnotherAccountOrOutsideThreeDaysOrOfAnotherAmountIsNotOffered() {
        val credit = draft(amount = lkr(25_000), direction = Direction.IN)

        assertTrue("another account", CaptureMatchRules.transferCounterparts(credit, cash.id, transferLegs("t1")).isEmpty())
        assertTrue("too late", CaptureMatchRules.transferCounterparts(credit, bank.id, transferLegs("t2", days = 4)).isEmpty())
        assertTrue("another amount", CaptureMatchRules.transferCounterparts(credit, bank.id, transferLegs("t3", amount = lkr(24_000))).isEmpty())
        assertTrue("not a transfer", CaptureMatchRules.transferCounterparts(credit, bank.id, listOf(entry(tx("plain-income", TransactionType.INCOME, lkr(25_000))))).isEmpty())
    }

    @Test
    fun theWindowForATransferIsThreeDays() {
        val credit = draft(amount = lkr(25_000), direction = Direction.IN)

        assertEquals(1, CaptureMatchRules.transferCounterparts(credit, bank.id, transferLegs("t1", days = 3)).size)
        assertEquals(1, CaptureMatchRules.transferCounterparts(credit, bank.id, transferLegs("t1", days = -3)).size)
    }

    @Test
    fun aTransferAlreadyEvidencedByThisMessageIsNotOfferedAgain() {
        val credit = draft(amount = lkr(25_000), direction = Direction.IN)
        val legs = transferLegs("t1").map { it.copy(evidenceIds = listOf(EvidenceId("d1"))) }

        assertTrue(CaptureMatchRules.transferCounterparts(credit, bank.id, legs).isEmpty())
    }

    @Test
    fun aVoidedTransferIsNotOffered() {
        val credit = draft(amount = lkr(25_000), direction = Direction.IN)
        val voided = transferLegs("t1").map { entry(it.current, voided = true) }

        assertTrue(CaptureMatchRules.transferCounterparts(credit, bank.id, voided).isEmpty())
    }

    // --- Other waiting messages ------------------------------------------------------------

    @Test
    fun anotherWaitingMessageWithTheSameAmountAndDayIsATwin() {
        val twin = draft(id = "d2")
        val different = draft(id = "d3", amount = lkr(900))
        val closed = draft(id = "d4", status = DraftStatus.CONFIRMED)
        val opposite = draft(id = "d5", direction = Direction.IN)

        val twins = CaptureMatchRules.pendingTwins(draft(), listOf(draft(), twin, different, closed, opposite))

        assertEquals(listOf("d2"), twins.map { it.id.value })
    }

    @Test
    fun aDraftWithoutAnAmountHasNoTwins() {
        assertTrue(CaptureMatchRules.pendingTwins(draft(amount = null), listOf(draft(id = "d2"))).isEmpty())
        assertNull(CaptureMatchRules.directionOf(Draft(DraftId("x"), DraftSource.SMS, emptyList(), DraftStatus.PENDING_REVIEW, T0)))
    }
}
