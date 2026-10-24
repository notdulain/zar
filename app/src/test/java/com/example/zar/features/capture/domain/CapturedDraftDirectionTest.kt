package com.example.zar.features.capture.domain

import com.example.zar.core.data.remote.firestore.DraftDocumentMapper
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.features.capture.domain.model.SmsParseResult
import com.example.zar.features.capture.domain.parser.BankSmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedDraftDirectionTest {
    private val purchase = "Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111."
    private val credit = "LKR 240.00 credited to AC **3333 for Food\n12/10/2026 12:21:02\nTo Inq Call 0112303050\nGet protected -Do not Share OTP"

    private fun proposalFor(text: String): DraftProposal.ActualTransaction {
        val parsed = (BankSmsParser().parse(text) as SmsParseResult.Parsed).sms
        val draft = CapturedDraftRules.draftFrom(CapturedDraftRules.draftIdFor(text), parsed, DraftSource.SHARED_TEXT, T0)
        return draft.proposals.single() as DraftProposal.ActualTransaction
    }

    @Test
    fun aDraftKeepsWhichWayTheMoneyWentEvenWhenItsTypeIsOnlyAGuess() {
        assertEquals(Direction.OUT, proposalFor(purchase).direction)
        val credited = proposalFor(credit)
        assertEquals(Direction.IN, credited.direction)
        assertNull("a credit is still not assumed to be income", credited.type)
    }

    @Test
    fun theDirectionSurvivesBeingStoredAndRead() {
        val draft = CapturedDraftRules.draftFrom(
            CapturedDraftRules.draftIdFor(credit),
            (BankSmsParser().parse(credit) as SmsParseResult.Parsed).sms, DraftSource.SMS, T0,
        )

        val read = DraftDocumentMapper.fromMap(DraftDocumentMapper.toMap(draft))

        assertEquals(draft, read)
        assertEquals(Direction.IN, (read?.proposals?.single() as DraftProposal.ActualTransaction).direction)
    }

    @Test
    fun aDraftStoredBeforeTheDirectionExistedStillReads() {
        val draft = CapturedDraftRules.draftFrom(
            CapturedDraftRules.draftIdFor(purchase),
            (BankSmsParser().parse(purchase) as SmsParseResult.Parsed).sms, DraftSource.SMS, T0,
        )
        val stored = DraftDocumentMapper.toMap(draft).toMutableMap()
        @Suppress("UNCHECKED_CAST")
        stored["proposals"] = (stored["proposals"] as List<Map<String, Any>>).map { it - "direction" }

        val read = DraftDocumentMapper.fromMap(stored)

        assertTrue(read != null)
        assertNull((read!!.proposals.single() as DraftProposal.ActualTransaction).direction)
    }
}
