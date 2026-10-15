package com.example.zar.features.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zar.core.data.remote.FirebaseUserSessionProvider
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.ReportingConversion
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Records written by one user are invisible and unreadable to the next user on the same phone. */
@RunWith(AndroidJUnit4::class)
class UserScopedDataTest {

    private val auth get() = EmulatorFirebase.auth
    private val firestore get() = EmulatorFirebase.firestore
    private val ledger by lazy { FirestoreLedgerRepository(firestore, FirebaseUserSessionProvider(auth)) }

    @Before
    fun setUp() = EmulatorFirebase.requireEmulators()

    @Test
    fun nextUserSeesNoneOfThePreviousUsersRecords() = runBlocking {
        val userA = auth.createUserWithEmailAndPassword(EmulatorFirebase.newEmail(), "password-a1").await().user!!.uid
        val lunch = Transaction(
            id = TransactionId("lunch-$userA"),
            accountId = AccountId("cash"),
            type = TransactionType.EXPENSE,
            amount = FiatMoney(85_000, Currencies.LKR),
            date = LocalDate.of(2026, 10, 14),
            reportingConversion = ReportingConversion.SameCurrency,
            spendingClass = SpendingClass.DISCRETIONARY,
            createdAt = Instant.now(),
        )
        ledger.saveTransaction(lunch)
        val seenByA = withTimeout(10_000) { ledger.observeLedger().first { it.isNotEmpty() } }
        assertEquals(lunch.id, seenByA.single().id)
        firestore.waitForPendingWrites().await()

        auth.signOut()
        auth.createUserWithEmailAndPassword(EmulatorFirebase.newEmail(), "password-b1").await()

        // B's ledger is empty...
        val seenByB = withTimeout(10_000) { ledger.observeLedger().first() }
        assertTrue(seenByB.isEmpty())

        // ...and reading A's records directly is refused by the security rules.
        try {
            firestore.collection("users").document(userA).collection("transactions").get(
                com.google.firebase.firestore.Source.SERVER,
            ).await()
            fail("User B could read user A's transactions")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
        auth.signOut()
    }
}
