package com.example.zar.features.export

import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.EvidenceId
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.export.domain.TransactionsCsv
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionsCsvTest {

    private val accounts = listOf(TestLedger.bank, TestLedger.cash, TestLedger.payoneer)

    private fun records(
        vararg transactions: Transaction,
        corrections: List<Correction> = emptyList(),
        voids: List<VoidMarker> = emptyList(),
        links: List<EvidenceLink> = emptyList(),
    ) = csvRecords(
        TransactionsCsv.build(LedgerProjector.project(transactions.toList(), corrections, voids, links), accounts).text,
    )

    @Test
    fun theHeaderListsEveryColumnTheStoryAsksFor() {
        val header = parseCsv(TransactionsCsv.build(emptyList(), accounts).text).single()
        assertEquals(TransactionsCsv.HEADER, header)
        listOf(
            "transaction_id", "currency", "amount", "original_amount", "corrected", "source",
            "spending_class", "status",
        ).forEach { assertTrue("missing $it", it in header) }
    }

    @Test
    fun anEmptyLedgerGivesAHeaderOnlyFile() {
        val doc = TransactionsCsv.build(emptyList(), accounts)
        assertEquals(0, doc.rowCount)
        assertEquals(1, parseCsv(doc.text).size)
    }

    @Test
    fun aRowCarriesItsStableIdCurrencyAmountAndSource() {
        val row = records(
            tx("exp-1", TransactionType.EXPENSE, lkr(1_250), spendingClass = SpendingClass.COMMITTED)
                .copy(merchant = "Keells", categoryId = "groceries"),
        ).single()
        assertEquals("exp-1", row["transaction_id"])
        assertEquals("ACTIVE", row["status"])
        assertEquals("EXPENSE", row["type"])
        assertEquals("OUT", row["direction"])
        assertEquals("1250.00", row["amount"])
        assertEquals("LKR", row["currency"])
        assertEquals("COMMITTED", row["spending_class"])
        assertEquals("groceries", row["category_id"])
        assertEquals("Keells", row["merchant"])
        assertEquals("MANUAL", row["source"])
        assertEquals("bank", row["account_id"])
        assertEquals("Bank", row["account_name"])
    }

    @Test
    fun incomeHasNoSpendingClass() {
        val row = records(tx("inc-1", TransactionType.INCOME, lkr(50_000))).single()
        assertEquals("IN", row["direction"])
        assertEquals("", row["spending_class"])
    }

    @Test
    fun correctedValuesLeadAndTheOriginalIsKept() {
        val row = records(
            tx("exp-1", TransactionType.EXPENSE, lkr(1_000)),
            corrections = listOf(
                TestLedger.correction("fix", "exp-1", TransactionChanges(amount = lkr(1_800), date = LocalDate.of(2026, 10, 12))),
            ),
        ).single()
        assertEquals("1800.00", row["amount"])
        assertEquals("2026-10-12", row["date"])
        assertEquals("true", row["corrected"])
        assertEquals("1", row["correction_count"])
        assertEquals("1000.00", row["original_amount"])
        assertEquals("2026-10-10", row["original_date"])
        assertTrue(row.getValue("last_corrected_at").isNotEmpty())
    }

    @Test
    fun anUncorrectedRowSaysSo() {
        val row = records(tx("exp-1", TransactionType.EXPENSE, lkr(1_000))).single()
        assertEquals("false", row["corrected"])
        assertEquals("0", row["correction_count"])
        assertEquals("1000.00", row["original_amount"])
        assertEquals("", row["last_corrected_at"])
    }

    @Test
    fun undoneEntriesAreKeptAndMarked() {
        val rows = records(
            tx("keep", TransactionType.EXPENSE, lkr(500)),
            tx("undone", TransactionType.EXPENSE, lkr(900)),
            voids = listOf(TestLedger.void("undone")),
        ).associateBy { it["transaction_id"] }
        assertEquals("ACTIVE", rows.getValue("keep")["status"])
        assertEquals("VOIDED", rows.getValue("undone")["status"])
        assertTrue(rows.getValue("undone").getValue("voided_at").isNotEmpty())
        assertEquals("", rows.getValue("keep")["voided_at"])
    }

    @Test
    fun transferLegsShareATransferId() {
        val rows = records(
            tx("t1-out", TransactionType.TRANSFER_OUT, lkr(5_000), transferId = "t1"),
            tx("t1-in", TransactionType.TRANSFER_IN, lkr(5_000), account = TestLedger.cash, transferId = "t1"),
        )
        assertEquals(setOf("t1"), rows.map { it["transfer_id"] }.toSet())
        assertEquals(setOf("TRANSFER_OUT", "TRANSFER_IN"), rows.map { it["type"] }.toSet())
    }

    @Test
    fun refundsPointAtTheirPurchase() {
        val row = records(tx("refund", TransactionType.REFUND, lkr(300), refundOf = "shoes")).single()
        assertEquals("shoes", row["refund_of_transaction_id"])
    }

    @Test
    fun theStoredLkrValueAndRateAreExportedNotTodaysRate() {
        val row = records(tx("pay", TransactionType.INCOME, usd(100), account = TestLedger.payoneer)).single()
        assertEquals("USD", row["currency"])
        assertEquals("100.00", row["amount"])
        assertEquals("CONVERTED", row["reporting_status"])
        assertEquals("30000.00", row["reporting_amount_lkr"])
        assertEquals("300", row["exchange_rate"])
    }

    @Test
    fun aMissingRateIsEmptyAndFlagged() {
        val row = records(
            tx("pay", TransactionType.INCOME, usd(100), account = TestLedger.payoneer,
                conversion = ReportingConversion.Missing(Currencies.LKR)),
        ).single()
        assertEquals("MISSING", row["reporting_status"])
        assertEquals("", row["reporting_amount_lkr"])
        assertEquals("", row["exchange_rate"])
    }

    @Test
    fun lkrRecordsNeedNoRate() {
        val row = records(tx("a", TransactionType.INCOME, lkr(10))).single()
        assertEquals("SAME", row["reporting_status"])
        assertEquals("10.00", row["reporting_amount_lkr"])
    }

    @Test
    fun cryptoKeepsItsExactPrecision() {
        val btc = com.example.zar.core.domain.model.money.CryptoAmount.of("0.00012345".toBigDecimal(), Currencies.BTC)
        val row = records(
            tx("buy", TransactionType.INCOME, btc, conversion = ReportingConversion.Missing(Currencies.LKR)),
        ).single()
        assertEquals("0.00012345", row["amount"])
        assertEquals("BTC", row["currency"])
    }

    @Test
    fun notesAndMerchantsWithCommasQuotesAndLineBreaksStayInOneCell() {
        val note = "Lunch, with \"friends\"\nsecond line"
        val row = records(
            tx("exp-1", TransactionType.EXPENSE, lkr(100)).copy(merchant = "Café, Colombo", note = note),
        ).single()
        assertEquals("Café, Colombo", row["merchant"])
        assertEquals(note, row["note"])
    }

    @Test
    fun aMerchantStartingWithAFormulaCharacterIsNeutralised() {
        val row = records(tx("exp-1", TransactionType.EXPENSE, lkr(100)).copy(merchant = "=HYPERLINK(\"x\")")).single()
        assertTrue(row.getValue("merchant").startsWith("'="))
    }

    @Test
    fun theSourceOfCapturedRecordsIsExportedButNeverTheMessageText() {
        val text = TransactionsCsv.build(
            LedgerProjector.project(
                listOf(tx("sms-1", TransactionType.EXPENSE, lkr(2_000)).copy(source = RecordSource.SMS)),
                emptyList(), emptyList(),
                listOf(EvidenceLink(EvidenceId("raw-sms-body-123"), com.example.zar.core.domain.model.TransactionId("sms-1"), TestLedger.T0)),
            ),
            accounts,
        ).text
        val row = csvRecords(text).single()
        assertEquals("SMS", row["source"])
        assertEquals("1", row["linked_evidence_count"])
        // Evidence is counted only; its identifier or content is never copied.
        assertFalse("raw-sms-body-123" in text)
    }

    @Test
    fun rowsAreOrderedOldestFirstThenByTimeAndId() {
        val rows = records(
            tx("c", TransactionType.EXPENSE, lkr(1), date = LocalDate.of(2026, 10, 12)),
            tx("a", TransactionType.EXPENSE, lkr(1), date = LocalDate.of(2026, 10, 10)).copy(time = LocalTime.of(14, 0)),
            tx("b", TransactionType.EXPENSE, lkr(1), date = LocalDate.of(2026, 10, 10)).copy(time = LocalTime.of(9, 30)),
        )
        assertEquals(listOf("b", "a", "c"), rows.map { it["transaction_id"] })
        assertEquals("09:30", rows.first()["time"])
    }

    @Test
    fun anUnknownAccountLeavesTheNameEmptyButKeepsTheId() {
        val orphan = LedgerProjector.project(listOf(tx("x", TransactionType.EXPENSE, lkr(1))), emptyList(), emptyList())
        val row = csvRecords(TransactionsCsv.build(orphan, emptyList()).text).single()
        assertEquals("bank", row["account_id"])
        assertEquals("", row["account_name"])
    }
}
