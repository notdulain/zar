package com.example.zar.core.data

import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.RejectedLedgerWrite
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.transactions.domain.manual.ManualEntryRules
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseDetails
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The real [FirestoreLedgerRepository] on the local Auth and Firestore emulators with the repository's own
 * rules. Run with `firebase emulators:start --only auth,firestore` and an Android emulator (host 10.0.2.2).
 * Test fixture data only.
 */
class LedgerWriteOutcomeTest {
    companion object {
        private lateinit var app: FirebaseApp
        private lateinit var db: FirebaseFirestore
        private var uid: UserId = UserId("not-connected")

        @JvmStatic @BeforeClass fun connect() = runBlocking {
            app = FirebaseApp.initializeApp(
                InstrumentationRegistry.getInstrumentation().targetContext,
                FirebaseOptions.Builder().setProjectId("demo-zar").setApplicationId("1:123:android:ledger-outcome")
                    .setApiKey("fake-emulator-key").build(),
                "ledger-outcome-${UUID.randomUUID()}",
            )
            val auth = FirebaseAuth.getInstance(app)
            auth.useEmulator("10.0.2.2", 9099)
            uid = UserId(checkNotNull(auth.signInAnonymously().await().user).uid)
            db = FirebaseFirestore.getInstance(app)
            db.useEmulator("10.0.2.2", 8080)
        }

        @JvmStatic @AfterClass fun disconnect() { app.delete() }
    }

    private val session = object : UserSessionProvider {
        private val user = MutableStateFlow<UserId?>(uid)
        override fun currentUserId() = user.value
        override fun observeUserId() = user
    }

    private fun repository() = FirestoreLedgerRepository(db, session)

    private val wallet = Account(
        AccountId("wallet"), "Wallet", AccountType.CASH, Currencies.LKR,
        OpeningBalance.Known(FiatMoney(0, Currencies.LKR)), ZarTime.today(),
    )

    /** What the manual form saves for "LKR 500 today", built by the same rules the form uses. */
    private fun expense(id: String = "exp-${UUID.randomUUID()}", title: String = "Lunch"): Transaction =
        ManualEntryRules.newExpense(
            TransactionId(id),
            ExpenseDetails(
                account = wallet,
                amount = FiatMoney(50_000, Currencies.LKR),
                date = ZarTime.today(),
                category = ExpenseCategory.COFFEE_DINING,
                spendingClass = ExpenseCategory.COFFEE_DINING.usualClass!!,
                title = title,
                description = null,
                time = LocalTime.of(13, 5),
            ),
            // Instants are stored as epoch milliseconds, so the fixture is millisecond-exact too.
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
        )

    private fun transactions() = db.collection("users").document(uid.value).collection("transactions")

    @Test
    fun anExpenseSavedOfflineAppearsOnceImmediatelyAndStaysAfterReconnect() = runBlocking {
        val repo = repository()
        val saved = expense()
        try {
            db.disableNetwork().await()
            repo.saveTransaction(saved)
            // No refresh, no network: the next local emission carries it, exactly once, with the saved values.
            val offline = withTimeout(5_000) { repo.observeLedger().first { list -> list.any { it.id == saved.id } } }
            assertEquals(1, offline.count { it.id == saved.id })
            assertEquals(saved, offline.single { it.id == saved.id }.current)
        } finally { db.enableNetwork().await() }

        // Retrying the identical save after reconnect must not duplicate it.
        repo.saveTransaction(saved)
        db.waitForPendingWrites().await()
        assertEquals(1, transactions().whereEqualTo("id", saved.id.value).get(Source.SERVER).await().size())
        assertEquals(1, repo.observeLedger().first().count { it.id == saved.id })
    }

    /**
     * The save returns normally and the entry is shown; the server then refuses it and Firestore rolls the local
     * copy back. That must not be silent: the refused records are reported so the person can try again. A
     * 121-character title is what the repository's rules refuse; a stale deployed ruleset would refuse a current
     * expense the same way.
     */
    @Test
    fun aServerRejectedExpenseIsReportedWithItsRecordInsteadOfSilentlyDropped() = runBlocking {
        val repo = repository()
        val refused = expense(title = "x".repeat(121))
        try {
            db.disableNetwork().await()
            repo.saveTransaction(refused)
            withTimeout(5_000) { repo.observeLedger().first { list -> list.any { it.id == refused.id } } }
            assertTrue(repo.observeRejectedWrites().first().isEmpty()) // locally accepted is not a failure
        } finally { db.enableNetwork().await() }

        // The rule refuses it on reconnect and the row leaves the ledger...
        withTimeout(15_000) { repo.observeLedger().first { list -> list.none { it.id == refused.id } } }
        assertTrue(transactions().document(refused.id.value).get(Source.SERVER).await().exists().not())
        // ...but the record is kept and reported, so nothing is lost without a signal.
        val reported = withTimeout(10_000) { repo.observeRejectedWrites().first { it.isNotEmpty() } }
        assertEquals(listOf(RejectedLedgerWrite(refused.id.value, listOf(refused))), reported)
    }

    @Test
    fun replayingAnAlreadyStoredExpenseIsNotReportedAsRejected() = runBlocking {
        val repo = repository()
        val saved = expense()
        repo.saveTransaction(saved)
        db.waitForPendingWrites().await()
        repo.saveTransaction(saved) // the rules deny the overwrite; the stored copy is intact and nothing was lost
        db.waitForPendingWrites().await()
        delay(1_000)
        assertTrue(repo.observeRejectedWrites().first().isEmpty())
        assertEquals(1, repo.observeLedger().first().count { it.id == saved.id })
    }

    @Test
    fun aRefusedSaveKeepsItsIdWhenTriedAgainAndLeavesTheListWhenDismissed() = runBlocking {
        val repo = repository()
        val refused = expense(title = "x".repeat(121))
        repo.saveTransaction(refused)
        withTimeout(10_000) { repo.observeRejectedWrites().first { it.isNotEmpty() } }

        // Trying again resubmits the same record under the same ID (the rule still refuses it, so it comes back).
        repo.retryRejectedWrite(refused.id.value)
        val again = withTimeout(10_000) { repo.observeRejectedWrites().first { it.any { w -> w.id == refused.id.value } } }
        assertEquals(1, again.count { it.id == refused.id.value })
        assertEquals(listOf(refused), again.single { it.id == refused.id.value }.transactions)

        repo.dismissRejectedWrite(refused.id.value)
        assertTrue(repo.observeRejectedWrites().first().isEmpty())
    }
}
