package com.example.zar.features.quicknote.data

import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.quicknote.QuickNoteTestSupport
import com.example.zar.features.quicknote.QuickNoteTestSupport.TODAY
import com.example.zar.features.quicknote.data.mapper.QuickNoteDraftMapper
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.requiresPurchaseSelection
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickNoteDraftMapperTest {

    private val created = Instant.parse("2026-10-21T04:00:00Z")

    private fun map(note: String, vararg proposals: Map<String, Any?>): Draft =
        QuickNoteDraftMapper.toDraft(mapOf("version" to 1, "proposals" to proposals.toList()), QuickNoteTestSupport.request(note), DraftId("d1"), created)

    private inline fun <reified T : DraftProposal> only(draft: Draft): T = draft.proposals.single() as T

    private fun actual(vararg extra: Pair<String, Any?>) = mapOf<String, Any?>(
        "kind" to "ACTUAL_TRANSACTION", "transactionType" to "EXPENSE", "amount" to "850", "currency" to "LKR",
        "accountId" to "boc", "date" to "2026-10-21", "spendingClass" to "DISCRETIONARY", "uncertainFields" to emptyList<String>(),
    ) + extra

    private fun lkr(minor: Long) = FiatMoney(minor, Currencies.LKR)

    // --- draft identity -----------------------------------------------------------------------------------

    @Test fun returnsPendingQuickNoteDraftWithStableIdentity() {
        val draft = map("lunch 850", actual())
        assertEquals(DraftId("d1"), draft.id)
        assertEquals(DraftSource.QUICK_NOTE, draft.source)
        assertEquals(DraftStatus.PENDING_REVIEW, draft.status)
        assertEquals(created, draft.createdAt)
        assertEquals("draft-d1-0", draft.recordIdFor(0))
    }

    @Test fun zeroProposalsIsASuccessfulEmptyDraft() {
        assertTrue(map("hello there").proposals.isEmpty())
    }

    // --- actual / expected / transfer ---------------------------------------------------------------------

    @Test fun mapsAnActualExpense() {
        val p = only<DraftProposal.ActualTransaction>(map("lunch 850 at keells, card", actual("category" to "food", "merchant" to "Keells", "note" to "lunch")))
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals(lkr(85_000), p.amount)
        assertEquals(AccountId("boc"), p.accountId)
        assertEquals(LocalDate.of(2026, 10, 21), p.date)
        assertEquals(SpendingClass.DISCRETIONARY, p.spendingClass)
        assertEquals("food", p.categoryId)
        assertEquals("Keells", p.merchant)
        assertTrue(p.missingFields.isEmpty())
        assertTrue(p.uncertainFields.isEmpty())
    }

    @Test fun separatesActualAndExpectedMoneyFromOneNote() {
        val draft = map(
            "got 150 now, other 150 later",
            actual("transactionType" to "INCOME", "amount" to "150", "spendingClass" to null),
            mapOf("kind" to "EXPECTED", "direction" to "IN", "amount" to "150", "currency" to "LKR", "dueDate" to "2026-11-05", "title" to "Second payment", "uncertainFields" to listOf("DATE")),
        )
        assertEquals(2, draft.proposals.size)
        val received = draft.proposals[0] as DraftProposal.ActualTransaction
        assertEquals(TransactionType.INCOME, received.type)
        val expected = draft.proposals[1] as DraftProposal.Expected
        assertEquals(Direction.IN, expected.direction)
        assertEquals(LocalDate.of(2026, 11, 5), expected.dueDate)
        assertEquals(setOf(DraftField.DATE), expected.uncertainFields)
    }

    @Test fun expectedPaymentWithNoDueDateOrTitleReportsThemMissing() {
        val p = only<DraftProposal.Expected>(map("will pay 5000", mapOf("kind" to "EXPECTED", "direction" to "OUT", "amount" to "5000", "currency" to "LKR")))
        assertEquals(setOf(DraftField.DATE, DraftField.TITLE), p.missingFields)
    }

    @Test fun sameCurrencyTransferDefaultsReceivedToSent() {
        val p = only<DraftProposal.TransferBetweenAccounts>(
            map("moved 5000 from boc to cash", mapOf("kind" to "TRANSFER", "accountId" to "boc", "toAccountId" to "cash", "amount" to "5000", "currency" to "LKR", "date" to "2026-10-21")),
        )
        assertEquals(lkr(500_000), p.sent)
        assertEquals(lkr(500_000), p.received)
        assertTrue(p.missingFields.isEmpty())
    }

    @Test fun crossCurrencyTransferKeepsSeparateSentAndReceived() {
        val p = only<DraftProposal.TransferBetweenAccounts>(
            map(
                "bought 50 usdt with 15000", mapOf(
                    "kind" to "TRANSFER", "accountId" to "boc", "toAccountId" to "binance-usdt", "amount" to "15000", "currency" to "LKR",
                    "receivedAmount" to "50", "receivedCurrency" to "USDT", "date" to "2026-10-21",
                ),
            ),
        )
        assertEquals(lkr(1_500_000), p.sent)
        assertEquals(CryptoAmount.of(BigDecimal("50"), Currencies.USDT), p.received)
        assertTrue(p.missingFields.isEmpty())
    }

    @Test fun crossCurrencyTransferWithoutReceivedAmountIsNeverDefaultedToSent() {
        val p = only<DraftProposal.TransferBetweenAccounts>(
            map("bought usdt with 15000", mapOf("kind" to "TRANSFER", "accountId" to "boc", "toAccountId" to "binance-usdt", "amount" to "15000", "currency" to "LKR", "date" to "2026-10-21")),
        )
        assertEquals(lkr(1_500_000), p.sent)
        assertNull(p.received)
        assertTrue(DraftField.AMOUNT in p.missingFields)
    }

    @Test fun transferToAnUnknownAccountDoesNotInventReceived() {
        val p = only<DraftProposal.TransferBetweenAccounts>(
            map("sent 5000 to my wallet", mapOf("kind" to "TRANSFER", "accountId" to "boc", "toAccountId" to "ghost", "amount" to "5000", "currency" to "LKR")),
        )
        assertNull(p.toAccountId)
        assertNull(p.received)
        assertEquals(setOf(DraftField.COUNTERPART_ACCOUNT, DraftField.AMOUNT, DraftField.DATE), p.missingFields)
    }

    @Test fun transferBetweenTheSameAccountClearsBothSides() {
        val p = only<DraftProposal.TransferBetweenAccounts>(
            map("5000 boc to boc", mapOf("kind" to "TRANSFER", "accountId" to "boc", "toAccountId" to "boc", "amount" to "5000", "currency" to "LKR")),
        )
        assertNull(p.fromAccountId)
        assertNull(p.toAccountId)
    }

    @Test fun transferIsNeverIncomeOrSpending() {
        val draft = map("5000 boc to cash", mapOf("kind" to "TRANSFER", "accountId" to "boc", "toAccountId" to "cash", "amount" to "5000", "currency" to "LKR", "transactionType" to "EXPENSE", "spendingClass" to "COMMITTED"))
        assertTrue(draft.proposals.single() is DraftProposal.TransferBetweenAccounts)
    }

    // --- accounts, currency ---------------------------------------------------------------------------------

    @Test fun hallucinatedAccountBecomesMissing() {
        val p = only<DraftProposal.ActualTransaction>(map("lunch 850", actual("accountId" to "made-up-account")))
        assertNull(p.accountId)
        assertTrue(DraftField.ACCOUNT in p.missingFields)
        assertEquals(lkr(85_000), p.amount)
    }

    @Test fun nonStringAccountIdIsMissing() {
        assertNull(only<DraftProposal.ActualTransaction>(map("lunch 850", actual("accountId" to 12))).accountId)
    }

    @Test fun amountInTheWrongCurrencyForItsAccountIsCleared() {
        val p = only<DraftProposal.ActualTransaction>(map("paid 850 dollars", actual("currency" to "USD")))
        assertNull(p.amount)
        assertTrue(DraftField.AMOUNT in p.missingFields)
    }

    @Test fun missingCurrencyUsesTheKnownAccountButNeverGuessesLkr() {
        assertEquals(lkr(85_000), only<DraftProposal.ActualTransaction>(map("lunch 850", actual("currency" to null))).amount)
        val noAccount = only<DraftProposal.ActualTransaction>(map("lunch 850", actual("currency" to null, "accountId" to null)))
        assertNull(noAccount.amount)
    }

    @Test fun unknownCurrencyIsCleared() {
        assertNull(only<DraftProposal.ActualTransaction>(map("lunch 850", actual("currency" to "XYZ", "accountId" to null))).amount)
        assertNull(only<DraftProposal.ActualTransaction>(map("lunch 850", actual("currency" to "XYZ"))).amount)
        assertNull(only<DraftProposal.ActualTransaction>(map("lunch 850", actual("currency" to 123))).amount)
    }

    @Test fun amountKeepsItsCurrencyWhenNoAccountIsKnown() {
        val p = only<DraftProposal.ActualTransaction>(map("got 120 usd", actual("transactionType" to "INCOME", "amount" to "120", "currency" to "USD", "accountId" to null, "spendingClass" to null)))
        assertEquals(FiatMoney(12_000, Currencies.USD), p.amount)
    }

    @Test fun cryptoAmountsAreExactAndLimitedToTheirPrecision() {
        val ok = only<DraftProposal.ActualTransaction>(map("got 0.00012345 btc", actual("transactionType" to "INCOME", "amount" to "0.00012345", "currency" to "BTC", "accountId" to null, "spendingClass" to null)))
        assertEquals(CryptoAmount.of(BigDecimal("0.00012345"), Currencies.BTC), ok.amount)
        val tooPrecise = only<DraftProposal.ActualTransaction>(map("got 0.123456789 btc", actual("transactionType" to "INCOME", "amount" to "0.123456789", "currency" to "BTC", "accountId" to null, "spendingClass" to null)))
        assertNull(tooPrecise.amount)
    }

    // --- exact money ---------------------------------------------------------------------------------------

    @Test fun invalidAmountsBecomeMissing() {
        for (bad in listOf("", "abc", "-100", "+100", "0", "0.00", "1e3", "1,250", "12.345", "NaN", "100 LKR", " ", "9".repeat(40), "٣٠٠")) {
            val p = only<DraftProposal.ActualTransaction>(map("note 100", actual("amount" to bad)))
            assertNull("'$bad' must be missing", p.amount)
            assertTrue(DraftField.AMOUNT in p.missingFields)
        }
        assertNull(only<DraftProposal.ActualTransaction>(map("note 100", actual("amount" to 850))).amount)
        assertNull(only<DraftProposal.ActualTransaction>(map("note 100", actual("amount" to 850.0))).amount)
    }

    @Test fun amountsParseWithoutFloatingPointLoss() {
        val p = only<DraftProposal.ActualTransaction>(map("paid 1250.50", actual("amount" to "1250.50")))
        assertEquals(lkr(125_050), p.amount)
        val big = only<DraftProposal.ActualTransaction>(map("paid 90071992547409.93", actual("amount" to "90071992547409.93")))
        assertEquals(lkr(9_007_199_254_740_993L), big.amount)
        val overflow = only<DraftProposal.ActualTransaction>(map("paid 99999999999999999", actual("amount" to "99999999999999999")))
        assertNull(overflow.amount)
    }

    // --- grounding / uncertainty ----------------------------------------------------------------------------

    @Test fun amountNotWrittenInTheNoteIsFlaggedUncertainButKept() {
        val p = only<DraftProposal.ActualTransaction>(map("lunch at keells", actual("amount" to "850")))
        assertEquals(lkr(85_000), p.amount)
        assertTrue(DraftField.AMOUNT in p.uncertainFields)
    }

    @Test fun groundedAmountIsNotFlagged() {
        assertTrue(only<DraftProposal.ActualTransaction>(map("lunch 850 at keells", actual())).uncertainFields.isEmpty())
    }

    @Test fun kAndLakhNormalizationGroundsTheAmount() {
        assertTrue(DraftField.AMOUNT !in only<DraftProposal.ActualTransaction>(map("got 5k", actual("amount" to "5000"))).uncertainFields)
        assertTrue(DraftField.AMOUNT !in only<DraftProposal.ActualTransaction>(map("advance 1.5 lakh", actual("amount" to "150000"))).uncertainFields)
        assertTrue(DraftField.AMOUNT in only<DraftProposal.ActualTransaction>(map("got 5k", actual("amount" to "50000"))).uncertainFields)
    }

    @Test fun nonLatinNumeralsGroundTheAmount() {
        val sinhala = map("බිල ෨෪෫෦ ක්", actual("amount" to "2450"))
        assertTrue(only<DraftProposal.ActualTransaction>(sinhala).uncertainFields.isEmpty())
    }

    @Test fun rangeAloneCannotSupplyAnExactAmount() {
        for (amount in listOf("2000", "2500", "3000")) {
            val p = only<DraftProposal.ActualTransaction>(map("2-3k for food", actual("amount" to amount)))
            assertNull(p.amount)
            assertTrue(DraftField.AMOUNT in p.missingFields)
        }
        val alternatives = only<DraftProposal.ActualTransaction>(map("paid 1000 or 1500", actual("amount" to "1000")))
        assertNull(alternatives.amount)
        val independent = only<DraftProposal.ActualTransaction>(map("paid 2500 today, next month 2-3k", actual("amount" to "2500")))
        assertEquals(lkr(250_000), independent.amount)
    }

    @Test fun modelUncertaintyIsPreservedAndUnknownFieldNamesAreDropped() {
        val p = only<DraftProposal.ActualTransaction>(map("lunch 850", actual("uncertainFields" to listOf("ACCOUNT", "CATEGORY", "NOT_A_FIELD", 5, null))))
        assertEquals(setOf(DraftField.ACCOUNT, DraftField.CATEGORY), p.uncertainFields)
    }

    @Test fun unknownAmountStaysMissingNotGuessed() {
        val p = only<DraftProposal.ActualTransaction>(map("paid 2-3k", actual("amount" to null)))
        assertNull(p.amount)
        assertTrue(DraftField.AMOUNT in p.missingFields)
    }

    // --- type rules ----------------------------------------------------------------------------------------

    @Test fun spendingClassAppliesToExpensesOnly() {
        for (type in listOf("INCOME", "REFUND")) {
            val p = only<DraftProposal.ActualTransaction>(map("note 850", actual("transactionType" to type, "spendingClass" to "COMMITTED")))
            assertNull(p.spendingClass)
        }
        val unclassified = only<DraftProposal.ActualTransaction>(map("note 850", actual("spendingClass" to null)))
        assertEquals(SpendingClass.UNKNOWN, unclassified.spendingClass)
        val bad = only<DraftProposal.ActualTransaction>(map("note 850", actual("spendingClass" to "LUXURY")))
        assertEquals(SpendingClass.UNKNOWN, bad.spendingClass)
    }

    @Test fun onlyIncomeExpenseAndRefundAreAccepted() {
        for (type in listOf("TRANSFER_IN", "ADJUSTMENT_OUT", "GIFT", "income", null)) {
            val p = only<DraftProposal.ActualTransaction>(map("note 850", actual("transactionType" to type)))
            assertNull("$type", p.type)
            assertTrue(DraftField.TYPE in p.missingFields)
        }
    }

    @Test fun refundsRequirePurchaseSelectionDownstream() {
        val refund = only<DraftProposal.ActualTransaction>(map("refund 850", actual("transactionType" to "REFUND", "spendingClass" to null)))
        assertTrue(refund.requiresPurchaseSelection)
        assertFalse(only<DraftProposal.ActualTransaction>(map("lunch 850", actual())).requiresPurchaseSelection)
    }

    // --- dates ---------------------------------------------------------------------------------------------

    @Test fun invalidAndImpossibleDatesBecomeMissing() {
        for (bad in listOf("2026-02-30", "21-10-2026", "tomorrow", "2026-10-22", "2026-1-5", "", 20261021)) {
            val p = only<DraftProposal.ActualTransaction>(map("lunch 850", actual("date" to bad)))
            assertNull("'$bad'", p.date)
            assertTrue(DraftField.DATE in p.missingFields)
        }
    }

    @Test fun validDatesAreNotSubjectToInventedCalendarLimits() {
        fun due(value: String) = only<DraftProposal.Expected>(map("pay 100", mapOf("kind" to "EXPECTED", "direction" to "OUT", "amount" to "100", "currency" to "LKR", "dueDate" to value, "title" to "t"))).dueDate
        assertEquals(LocalDate.of(2026, 12, 1), due("2026-12-01"))
        assertEquals(LocalDate.of(2099, 1, 1), due("2099-01-01"))
        assertEquals(LocalDate.of(1999, 12, 31), only<DraftProposal.ActualTransaction>(map("paid 850 in 1999", actual("date" to "1999-12-31"))).date)
        assertNull(due("2026-13-01"))
    }

    // --- text ----------------------------------------------------------------------------------------------

    @Test fun textFieldsAreCleanedAndBounded() {
        val p = only<DraftProposal.ActualTransaction>(map("lunch 850", actual("merchant" to "  Keells\u0000\n ", "note" to "n".repeat(500), "category" to "Food & Drink!!")))
        assertEquals("Keells", p.merchant)
        assertEquals(300, p.note!!.length)
        assertNull("free-form categories are not accepted as ids", p.categoryId)
        assertNull(only<DraftProposal.ActualTransaction>(map("lunch 850", actual("merchant" to "   "))).merchant)
    }

    @Test fun resultNeverCarriesTheRawNote() {
        val draft = map("secret-note 850", actual("note" to null))
        assertFalse(draft.toString().contains("secret-note"))
    }

    // --- invalid top-level contract ------------------------------------------------------------------------

    private fun assertInvalid(raw: Any?) {
        assertThrows(QuickNoteDraftError.InvalidResponse::class.java) {
            QuickNoteDraftMapper.toDraft(if (raw is Map<*, *>) mapOf("version" to 1) + raw else raw, QuickNoteTestSupport.request("lunch 850"), DraftId("d1"), created)
        }
    }

    @Test fun invalidTopLevelShapesAreRejected() {
        assertInvalid(null)
        assertInvalid("text")
        assertInvalid(listOf<Any>())
        assertInvalid(mapOf("proposals" to "x"))
        assertInvalid(mapOf("proposals" to null))
        assertInvalid(mapOf("other" to emptyList<Any>()))
    }

    @Test fun unsupportedKindsOrShapesAreRejected() {
        assertInvalid(mapOf("proposals" to listOf(mapOf("kind" to "POST_NOW"))))
        assertInvalid(mapOf("proposals" to listOf(mapOf("kind" to null))))
        assertInvalid(mapOf("proposals" to listOf("not a map")))
        assertInvalid(mapOf("proposals" to listOf(null)))
    }

    @Test fun tooManyProposalsAreRejected() {
        assertInvalid(mapOf("proposals" to List(6) { actual() }))
        assertEquals(5, map("note 850", *Array(5) { actual() }).proposals.size)
    }

    @Test fun malformedOptionalValuesNeverCrash() {
        val weird = mapOf<String, Any?>(
            "kind" to "ACTUAL_TRANSACTION", "transactionType" to 5, "amount" to mapOf("a" to 1), "currency" to listOf("LKR"),
            "accountId" to true, "date" to Any(), "spendingClass" to 3, "category" to 9, "merchant" to listOf(1), "note" to 1.5,
            "uncertainFields" to "AMOUNT",
        )
        val p = only<DraftProposal.ActualTransaction>(map("note 100", weird))
        assertEquals(setOf(DraftField.TYPE, DraftField.AMOUNT, DraftField.ACCOUNT, DraftField.DATE), p.missingFields)
        assertTrue(p.uncertainFields.isEmpty())
    }

    @Test fun moneyIsMoney() {
        val m: Money = only<DraftProposal.ActualTransaction>(map("lunch 850", actual())).amount!!
        assertTrue(m.isPositive)
    }

    @Test fun unsupportedMissingOrMalformedWireVersionsAreRejected() {
        for (version in listOf(null, 2, 1.5, "1", true)) {
            assertThrows(QuickNoteDraftError.InvalidResponse::class.java) {
                QuickNoteDraftMapper.toDraft(mapOf("version" to version, "proposals" to emptyList<Any>()),
                    QuickNoteTestSupport.request("lunch 850"), DraftId("d1"), created)
            }
        }
        val supported = QuickNoteDraftMapper.toDraft(mapOf("version" to 1L, "proposals" to emptyList<Any>()),
            QuickNoteTestSupport.request("lunch 850"), DraftId("d1"), created)
        assertTrue(supported.proposals.isEmpty())
    }
}
