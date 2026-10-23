package com.example.zar.core.data.remote.firestore

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.correction
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.TestLedger.void
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ReportingConversion
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerDocumentMapperTest {

    @Test
    fun roundTripsConvertedFiatTransaction() {
        val original = tx("usd", TransactionType.INCOME, usd(100), account = payoneer).copy(note = "Invoice 4")
        assertEquals(original, LedgerDocumentMapper.transactionFromMap(LedgerDocumentMapper.transactionToMap(original)))
    }

    @Test
    fun selectedTimeRoundTripsAndLegacyDocumentsRemainReadable() {
        val original = tx("timed", TransactionType.EXPENSE, lkr(850)).copy(time = LocalTime.of(9, 5))
        val map = LedgerDocumentMapper.transactionToMap(original)
        assertEquals("09:05", map["time"])
        assertEquals(original, LedgerDocumentMapper.transactionFromMap(map))

        val legacy = map - "time"
        assertNull(LedgerDocumentMapper.transactionFromMap(legacy)?.time)
        assertNull(LedgerDocumentMapper.transactionFromMap(map + ("time" to "25:61")))
    }

    @Test
    fun roundTripsCryptoWithoutPrecisionLoss() {
        val amount = CryptoAmount.parse("0.12345678", Currencies.BTC)!!
        val original = tx("btc", TransactionType.ADJUSTMENT_IN, amount, conversion = ReportingConversion.Missing(Currencies.LKR))
            .copy(accountId = AccountId("binance-btc"))
        val map = LedgerDocumentMapper.transactionToMap(original)
        assertEquals("0.12345678", (map["amount"] as Map<*, *>)["quantity"])
        assertEquals(original, LedgerDocumentMapper.transactionFromMap(map))
    }

    @Test
    fun storesFiatAsIntegerMinorUnits() {
        val map = LedgerDocumentMapper.transactionToMap(tx("x", TransactionType.EXPENSE, lkr(1_250)))
        assertEquals(125_000L, (map["amount"] as Map<*, *>)["minor"])
        assertFalse(map.containsKey("note"))
    }

    @Test
    fun correctionKeepsClearedAndUnchangedFieldsApart() {
        val original = correction("c1", "x", TransactionChanges(amount = lkr(5), note = FieldUpdate.Clear, merchant = FieldUpdate.Set("Keells")))
        val map = LedgerDocumentMapper.correctionToMap(original)
        val changes = map["changes"] as Map<*, *>
        assertFalse(changes.containsKey("categoryId"))
        assertNull(changes["note"])
        assertEquals(original, LedgerDocumentMapper.correctionFromMap(map))
    }

    @Test
    fun roundTripsVoidMarker() {
        val marker = void("x")
        assertEquals(marker, LedgerDocumentMapper.voidFromMap(LedgerDocumentMapper.voidToMap(marker)))
    }

    @Test
    fun malformedDocumentIsSkipped() {
        assertNull(LedgerDocumentMapper.transactionFromMap(mapOf("id" to "x", "createdAt" to T0.toEpochMilli())))
    }
    @Test
    fun roundTripsTheCategoryRuleThatFiledAnExpense() {
        val original = tx("filed", TransactionType.EXPENSE, lkr(850)).copy(categoryId = "groceries", appliedRuleId = "r1", appliedRuleVersion = 3)
        val map = LedgerDocumentMapper.transactionToMap(original)
        assertEquals("r1", map["appliedRuleId"])
        assertEquals(3, map["appliedRuleVersion"])
        assertEquals(original, LedgerDocumentMapper.transactionFromMap(map))
    }

    @Test
    fun anExpenseNoRuleFiledHasNoRuleFields() {
        val map = LedgerDocumentMapper.transactionToMap(tx("plain", TransactionType.EXPENSE, lkr(850)))
        assertFalse(map.containsKey("appliedRuleId"))
        assertFalse(map.containsKey("appliedRuleVersion"))
        assertNull(LedgerDocumentMapper.transactionFromMap(map)!!.appliedRuleId)
    }
}
