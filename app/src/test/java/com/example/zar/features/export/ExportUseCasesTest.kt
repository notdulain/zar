package com.example.zar.features.export

import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.export.domain.model.ExportKind
import com.example.zar.features.export.domain.usecase.BuildExportUseCase
import com.example.zar.features.export.domain.usecase.ObserveExportCountsUseCase
import com.example.zar.features.export.domain.usecase.SaveExportUseCase
import com.example.zar.features.freelance.FakeExpectedPaymentRepository
import com.example.zar.features.freelance.FreelanceFixtures
import com.example.zar.features.freelance.FreelanceFixtures.TODAY
import com.example.zar.features.home.StaticLedgerRepository
import com.example.zar.features.plans.FakePlanAccounts
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportUseCasesTest {

    private val ledger = StaticLedgerRepository()
    private val expected = FakeExpectedPaymentRepository()
    private val accounts = FakePlanAccounts(listOf(TestLedger.bank, TestLedger.payoneer))

    private fun build(session: FakeSession = FakeSession()) = BuildExportUseCase(session, ledger, expected, accounts)

    private fun setLedger(vararg transactions: com.example.zar.core.domain.model.Transaction) {
        ledger.entries.value = LedgerProjector.project(transactions.toList(), emptyList(), emptyList())
    }

    @Test
    fun theTransactionsExportIsNamedByKindAndDate() = runTest {
        setLedger(tx("a", TransactionType.EXPENSE, lkr(500)))
        val file = build()(ExportKind.TRANSACTIONS, TODAY).getOrThrow()
        assertEquals(ExportKind.TRANSACTIONS, file.kind)
        assertEquals("zar-transactions-2026-10-15.csv", file.fileName)
        assertEquals(1, file.rowCount)
        assertEquals("a", csvRecords(file.csv).single()["transaction_id"])
    }

    @Test
    fun theExpectedPaymentsExportIsAnotherFileWithOnlyExpectedMoney() = runTest {
        setLedger(tx("a", TransactionType.EXPENSE, lkr(500)))
        expected.payments.value = listOf(FreelanceFixtures.milestone("m1", "p1", usd(1_000)))

        val file = build()(ExportKind.EXPECTED_PAYMENTS, TODAY).getOrThrow()
        assertEquals("zar-expected-payments-2026-10-15.csv", file.fileName)
        assertEquals(1, file.rowCount)
        val row = csvRecords(file.csv).single()
        assertEquals("m1", row["expected_payment_id"])
        // A recorded expense never leaks into the expected-payments file.
        assertFalse("transaction_id" in row)
    }

    @Test
    fun theTwoFilesNeverShareContent() = runTest {
        setLedger(tx("a", TransactionType.INCOME, lkr(500)))
        expected.payments.value = listOf(FreelanceFixtures.milestone("m1", "p1", usd(1_000)))

        val transactions = build()(ExportKind.TRANSACTIONS, TODAY).getOrThrow()
        val expectedFile = build()(ExportKind.EXPECTED_PAYMENTS, TODAY).getOrThrow()
        assertFalse("m1" in transactions.csv)
        assertFalse("INCOME" in expectedFile.csv)
    }

    @Test
    fun noRecordsGivesAnEmptyFileTheScreenCanRefuseToSave() = runTest {
        val file = build()(ExportKind.TRANSACTIONS, TODAY).getOrThrow()
        assertEquals(0, file.rowCount)
        assertEquals(1, parseCsv(file.csv).size)
    }

    @Test
    fun aSignedOutUserCannotExport() = runTest {
        setLedger(tx("a", TransactionType.EXPENSE, lkr(500)))
        ExportKind.entries.forEach { kind ->
            val result = build(FakeSession(user = null))(kind, TODAY)
            assertTrue(result.exceptionOrNull() is LedgerError.NotSignedIn)
        }
    }

    @Test
    fun accountNamesComeFromTheUsersAccounts() = runTest {
        setLedger(tx("a", TransactionType.EXPENSE, lkr(500)))
        val file = build()(ExportKind.TRANSACTIONS, TODAY).getOrThrow()
        assertEquals("Bank", csvRecords(file.csv).single()["account_name"])
    }

    @Test
    fun countsFollowSavedRecords() = runTest {
        val counts = ObserveExportCountsUseCase(ledger, expected)
        assertEquals(0, counts().first().transactions)

        setLedger(tx("a", TransactionType.EXPENSE, lkr(1)), tx("b", TransactionType.INCOME, lkr(2)))
        expected.payments.value = listOf(FreelanceFixtures.milestone("m1", "p1", usd(1)))
        val now = counts().first()
        assertEquals(2, now.transactions)
        assertEquals(1, now.expectedPayments)
        assertEquals(2, now.of(ExportKind.TRANSACTIONS))
        assertEquals(1, now.of(ExportKind.EXPECTED_PAYMENTS))
    }

    @Test
    fun savingHandsTheFileToTheWriterAtTheChosenLocation() = runTest {
        setLedger(tx("a", TransactionType.EXPENSE, lkr(500)))
        val file = build()(ExportKind.TRANSACTIONS, TODAY).getOrThrow()
        val writer = RecordingWriter()

        assertTrue(SaveExportUseCase(writer)("content://docs/1", file).isSuccess)
        assertEquals(listOf("content://docs/1" to file), writer.written)
    }

    @Test
    fun aWriteFailureIsReportedNotThrown() = runTest {
        val file = build()(ExportKind.TRANSACTIONS, TODAY).getOrThrow()
        val writer = RecordingWriter().apply { failing = true }

        val result = SaveExportUseCase(writer)("content://docs/1", file)
        assertTrue(result.exceptionOrNull() is IOException)
        assertNull(writer.written.firstOrNull())
    }
}
