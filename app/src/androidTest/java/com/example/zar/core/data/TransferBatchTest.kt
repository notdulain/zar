package com.example.zar.core.data

import com.example.zar.core.data.remote.FirebaseUserSessionProvider
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.auth.EmulatorFirebase
import com.google.firebase.firestore.Source
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** These tests exercise real Firestore batches and security rules on the local emulator. */
class TransferBatchTest {
    @Before fun requireLocalEmulators() = EmulatorFirebase.requireEmulators()

    private fun transfer(invalidFee: Boolean = false): Transfer {
        val fee = Transaction(
            id = TransactionId("transfer-fee"), accountId = AccountId("bank"), type = TransactionType.EXPENSE,
            amount = FiatMoney(3_000, Currencies.LKR), date = LocalDate.of(2026, 10, 20),
            reportingConversion = ReportingConversion.SameCurrency, spendingClass = SpendingClass.COMMITTED,
            merchant = if (invalidFee) "x".repeat(121) else "Transfer fee", createdAt = Instant.now(),
        )
        return Transfer(
            id = TransferId("transfer"), fromAccountId = AccountId("bank"), toAccountId = AccountId("cash"),
            sent = FiatMoney(500_000, Currencies.LKR), received = FiatMoney(500_000, Currencies.LKR),
            date = fee.date, sentConversion = ReportingConversion.SameCurrency,
            receivedConversion = ReportingConversion.SameCurrency, createdAt = fee.createdAt, fee = fee,
        )
    }

    @Test
    fun transferAndFeeAreStoredTogether() = runBlocking {
        withTimeout(20_000) {
            val auth = EmulatorFirebase.auth
            val firestore = EmulatorFirebase.firestore
            val uid = auth.createUserWithEmailAndPassword(EmulatorFirebase.newEmail(), "test-password-1").await().user!!.uid
            try {
                val ledger = FirestoreLedgerRepository(firestore, FirebaseUserSessionProvider(auth))
                ledger.saveTransfer(transfer())
                firestore.waitForPendingWrites().await()
                val records = firestore.collection("users").document(uid).collection("transactions").get(Source.SERVER).await()
                assertEquals(setOf("transfer-out", "transfer-in", "transfer-fee"), records.documents.map { it.id }.toSet())
                assertEquals("EXPENSE", records.documents.single { it.id == "transfer-fee" }.getString("type"))
            } finally { auth.signOut() }
        }
    }

    @Test
    fun rejectedFeeLeavesNeitherTransferLegStored() = runBlocking {
        withTimeout(20_000) {
            val auth = EmulatorFirebase.auth
            val firestore = EmulatorFirebase.firestore
            val uid = auth.createUserWithEmailAndPassword(EmulatorFirebase.newEmail(), "test-password-1").await().user!!.uid
            try {
                val ledger = FirestoreLedgerRepository(firestore, FirebaseUserSessionProvider(auth))
                ledger.saveTransfer(transfer(invalidFee = true))
                firestore.waitForPendingWrites().await()
                val records = firestore.collection("users").document(uid).collection("transactions").get(Source.SERVER).await()
                assertTrue(records.isEmpty)
            } finally { auth.signOut() }
        }
    }
}
