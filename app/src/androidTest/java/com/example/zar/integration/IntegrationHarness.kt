package com.example.zar.integration

import com.example.zar.core.data.remote.FirebaseUserSessionProvider
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.data.remote.firestore.FirestoreDraftRepository
import com.example.zar.core.data.remote.firestore.FirestoreExpectedPaymentRepository
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.data.remote.firestore.LedgerDocumentMapper
import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.usecase.CorrectTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.features.accounts.data.repository.FirestoreAccountRepository
import com.example.zar.features.auth.IsolatedFirebaseApp
import com.example.zar.features.auth.step
import com.example.zar.features.quicknote.domain.usecase.ConfirmQuickNoteProposalUseCase
import com.example.zar.features.transactions.data.category.FirestoreExpenseCategoryRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * SCRUM-21 integration fixture: one isolated Firebase client on the local emulators (project demo-zar) with the
 * production Firestore repositories and domain use cases wired exactly as Hilt wires them. A fresh anonymous user per
 * instance keeps test data synthetic and separate. Never touches the live project.
 */
class IntegrationHarness private constructor(val client: IsolatedFirebaseApp) {
    val db get() = client.db
    val auth get() = client.auth
    val session = FirebaseUserSessionProvider(auth)

    val ledger = FirestoreLedgerRepository(db, session)
    val accounts = FirestoreAccountRepository(db, session)
    val drafts = FirestoreDraftRepository(db, session)
    val expected = FirestoreExpectedPaymentRepository(db, session)
    val categories = FirestoreExpenseCategoryRepository(db, session)

    val saveTransaction = SaveTransactionUseCase(ledger)
    val saveTransfer = SaveTransferUseCase(ledger)
    val correct = CorrectTransactionUseCase(ledger, accounts)
    val void = VoidTransactionUseCase(ledger)
    val confirmQuickNote = ConfirmQuickNoteProposalUseCase(accounts, ledger, expected, saveTransaction, saveTransfer, categories)

    val uid: String get() = checkNotNull(session.currentUserId()) { "nobody is signed in" }.value

    fun userId(): UserId = UserId(uid)

    private fun collection(name: String) =
        db.collection(FirestoreCollections.USERS).document(uid).collection(name)

    /** What the server stores, read straight from it (not the cache). Requires the network to be enabled. */
    suspend fun serverDocs(name: String): List<DocumentSnapshot> =
        step("read server $name") { collection(name).get(Source.SERVER).await().documents }

    /** The ledger exactly as the server holds it, projected by the same pure function the app uses. */
    suspend fun serverLedger(): List<LedgerEntry> = LedgerProjector.project(
        serverDocs(FirestoreCollections.TRANSACTIONS).mapNotNull { it.data?.let(LedgerDocumentMapper::transactionFromMap) },
        serverDocs(FirestoreCollections.CORRECTIONS).mapNotNull { it.data?.let(LedgerDocumentMapper::correctionFromMap) },
        serverDocs(FirestoreCollections.VOIDS).mapNotNull { it.data?.let(LedgerDocumentMapper::voidFromMap) },
        serverDocs(FirestoreCollections.EVIDENCE_LINKS).mapNotNull { it.data?.let(LedgerDocumentMapper::evidenceLinkFromMap) },
    )

    /** The ledger as this phone shows it (local cache plus pending writes). */
    suspend fun localLedger(): List<LedgerEntry> = step("read local ledger") { ledger.observeLedger().first() }

    suspend fun seedAccount(
        id: String,
        name: String = id,
        type: AccountType = AccountType.BANK,
        openingMinor: Long = 1_000_000,
        openingDate: LocalDate = ZarTime.today().minusDays(30),
    ): Account {
        val account = Account(
            AccountId(id), name, type, Currencies.LKR,
            OpeningBalance.Known(FiatMoney(openingMinor, Currencies.LKR)), openingDate,
        )
        accounts.saveAccount(account)
        flush()
        return account
    }

    /** Waits until every queued write has been acknowledged by the server. */
    suspend fun flush() = step("wait for pending writes") { db.waitForPendingWrites().await() }

    suspend fun offline() = step("disable network") { db.disableNetwork().await() }

    suspend fun online() = step("enable network") { db.enableNetwork().await() }

    fun balance(account: Account, entries: List<LedgerEntry>): AccountBalance =
        AccountBalanceCalculator.calculate(account, entries)

    /** Received/spent over every date a fixture can use (a captured message carries its own bank date). */
    fun summaryToday(entries: List<LedgerEntry>): PeriodSummary =
        LedgerSummaryCalculator.summarize(entries, LocalDate.of(2000, 1, 1)..ZarTime.today().plusDays(400))

    fun close() = client.close()

    companion object {
        /** Opens a client and signs in a brand-new anonymous user. */
        suspend fun open(label: String): IntegrationHarness {
            val client = IsolatedFirebaseApp.open("d08-$label-${UUID.randomUUID()}")
            step("sign in") { client.auth.signInAnonymously().await() }
            return IntegrationHarness(client)
        }
    }
}

/**
 * Reads what the Firestore emulator really holds, bypassing security rules with the emulator's `Bearer owner` token,
 * so a test can prove that a record is absent from a user's collection without being that user. Emulator only.
 */
object EmulatorAdmin {
    /** Document id → raw JSON of `users/{uid}/{collection}`. Empty when the collection has no documents. */
    suspend fun documents(uid: String, collection: String): Map<String, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = java.net.URL(
            "http://${IsolatedFirebaseApp.EMULATOR_HOST}:${IsolatedFirebaseApp.FIRESTORE_PORT}" +
                "/v1/projects/demo-zar/databases/(default)/documents/users/$uid/$collection?pageSize=300",
        )
        val connection = url.openConnection() as java.net.HttpURLConnection
        try {
            connection.setRequestProperty("Authorization", "Bearer owner")
            check(connection.responseCode == 200) { "admin read failed: ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val docs = org.json.JSONObject(body).optJSONArray("documents") ?: return@withContext emptyMap()
            (0 until docs.length()).associate { i ->
                val doc = docs.getJSONObject(i)
                doc.getString("name").substringAfterLast('/') to doc.toString()
            }
        } finally {
            connection.disconnect()
        }
    }
}
