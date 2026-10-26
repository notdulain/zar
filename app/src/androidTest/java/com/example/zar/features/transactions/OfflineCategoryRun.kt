package com.example.zar.features.transactions

import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.auth.IsolatedFirebaseApp
import com.example.zar.features.auth.step
import com.example.zar.features.transactions.data.category.FirestoreExpenseCategoryRepository
import com.example.zar.features.transactions.domain.category.CategoryLabel
import com.example.zar.features.transactions.domain.category.CategoryNameRules
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.time.Instant
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * One offline custom-category scenario with an expense that references the new category, run in stages so the same
 * steps serve a client restart in one process and a real process restart across two instrumentation runs.
 *
 * Why every step is bounded and nothing awaits an offline write: `DocumentReference.set(...)` completes only when
 * the *server* acknowledges the write. While the network is disabled (or unreachable) that acknowledgement never
 * arrives, so `set(...).await()` waits forever. The write itself is accepted locally at once, so the stages look at
 * the local cache and the pending-write flag instead, and only await the acknowledgement after reconnecting.
 *
 * Stages: [writeOffline] (network off, writes queued, server proven empty), [restartOffline] (a restarted client that
 * cannot reach the server reads the same writes back from disk), [reconnect] (a reachable client flushes them and the
 * server ends up with exactly one category and the expense).
 */
class OfflineCategoryRun(private val run: String) {
    val appName = "offline-run-$run"
    private val email = "offline-$run@zar.test"
    private val password = "offline-password-1"
    val categoryId = "${CustomCategoryIds.PREFIX}00000000-0000-4000-8000-${run.padStart(12, '0').takeLast(12)}"
    val expenseId = "offline-expense-$run"
    private val categoryName = "Pets"

    private fun category() = CustomExpenseCategory(
        categoryId, categoryName, CategoryNameRules.keyOf(categoryName), Instant.ofEpochMilli(1_790_000_000_000),
    )

    private fun expense() = mapOf(
        "id" to expenseId, "accountId" to "bank", "type" to "EXPENSE",
        "amount" to mapOf("currency" to "LKR", "kind" to "FIAT", "digits" to 2, "minor" to 100_000L),
        "date" to "2026-10-10", "conversion" to mapOf("status" to "SAME"),
        "spendingClass" to "DISCRETIONARY", "source" to "MANUAL", "createdAt" to 1_790_000_000_000L,
        "categoryId" to categoryId,
    )

    private fun categoryDoc(db: FirebaseFirestore, uid: String) =
        db.collection("users").document(uid).collection("expenseCategories").document(categoryId)

    private fun expenseDoc(db: FirebaseFirestore, uid: String) =
        db.collection("users").document(uid).collection("transactions").document(expenseId)

    private fun repository(db: FirebaseFirestore, uid: String) = FirestoreExpenseCategoryRepository(
        db,
        object : UserSessionProvider {
            private val user = MutableStateFlow<UserId?>(UserId(uid))
            override fun currentUserId() = user.value
            override fun observeUserId() = user
        },
    )

    /** The state of both documents in the local cache. Never waits for the network. */
    private suspend fun cached(db: FirebaseFirestore, uid: String): Pair<DocumentSnapshot, DocumentSnapshot> =
        categoryDoc(db, uid).get(Source.CACHE).await() to expenseDoc(db, uid).get(Source.CACHE).await()

    /** Logs the process id, so a log shows the stages ran in different processes after a kill. */
    private fun stage(name: String) = println("OfflineCategoryRun: $name run=$run pid=${android.os.Process.myPid()}")

    /** A separate client (its own cache) that only looks at what the server holds. */
    private suspend fun serverView(): Pair<DocumentSnapshot, DocumentSnapshot> {
        val verifier = IsolatedFirebaseApp.open("verifier-$run-${System.nanoTime()}")
        return try {
            val uid = step("verifier sign-in") { verifier.signInOrCreate(email, password) }
            step("server read") {
                categoryDoc(verifier.db, uid).get(Source.SERVER).await() to expenseDoc(verifier.db, uid).get(Source.SERVER).await()
            }
        } finally { verifier.close() }
    }

    /** Stage 1. Returns the uid. The client is left open: the caller decides how it dies. */
    suspend fun writeOffline(client: IsolatedFirebaseApp): String {
        stage("writeOffline")
        val uid = step("sign in") { client.signInOrCreate(email, password) }
        val disableMillis = measureTimeMillis { step("disableNetwork", 5_000) { client.db.disableNetwork().await() } }
        println("OfflineCategoryRun: disableNetwork returned in $disableMillis ms")

        // create() accepts the write locally and gives the server 4 s to answer; offline it just returns.
        val createMillis = measureTimeMillis { step("create category offline", 10_000) { repository(client.db, uid).create(category()) } }
        println("OfflineCategoryRun: create returned in $createMillis ms")

        // The expense write is issued but not awaited: its Task cannot complete before the server acknowledges it.
        val expenseWrite = expenseDoc(client.db, uid).set(expense())
        val (cat, exp) = step("cache read after offline writes") { cached(client.db, uid) }
        assertTrue("category is in the local cache", cat.exists())
        assertTrue("category write is still pending", cat.metadata.hasPendingWrites())
        assertTrue("expense is in the local cache", exp.exists())
        assertTrue("expense write is still pending", exp.metadata.hasPendingWrites())
        delay(1_500)
        assertFalse("an offline set() must not complete without a server ACK", expenseWrite.isComplete)

        // The server has neither document: nothing reached it while offline.
        val (serverCat, serverExp) = serverView()
        assertFalse("category must not be on the server yet", serverCat.exists())
        assertFalse("expense must not be on the server yet", serverExp.exists())
        return uid
    }

    /** Stage 2. [client] was opened against an unreachable server on the same persistent cache. */
    suspend fun restartOffline(client: IsolatedFirebaseApp) {
        stage("restartOffline")
        val uid = step("sign in after restart") { client.signInOrCreate(email, password) }
        val (cat, exp) = step("cache read after restart") { cached(client.db, uid) }
        assertTrue("category survived the restart on disk", cat.exists())
        assertTrue("category is still unsent", cat.metadata.hasPendingWrites())
        assertTrue("expense survived the restart on disk", exp.exists())
        assertTrue("expense is still unsent", exp.metadata.hasPendingWrites())
        assertEquals(categoryId, exp.getString("categoryId"))
        val restored = step("repository reads the cache") { repository(client.db, uid).currentCustomCategories() }
        val label = ExpenseCategoryCatalog(restored).label(categoryId)
        assertEquals(categoryName, (label as CategoryLabel.Custom).name)
    }

    /** Stage 3. [client] can reach the server: the queued writes are delivered once, then confirmed on the server. */
    suspend fun reconnect(client: IsolatedFirebaseApp) {
        stage("reconnect")
        val uid = step("sign in to reconnect") { client.signInOrCreate(email, password) }
        step("waitForPendingWrites after reconnect", 30_000) { client.db.waitForPendingWrites().await() }
        val (serverCat, serverExp) = serverView()
        assertTrue("category reached the server", serverCat.exists())
        assertEquals(categoryName, serverCat.getString("name"))
        assertTrue("expense reached the server", serverExp.exists())
        assertEquals(categoryId, serverExp.getString("categoryId"))
        val categories = step("count categories") {
            client.db.collection("users").document(uid).collection("expenseCategories").get(Source.SERVER).await()
        }
        assertEquals("no duplicate category", 1, categories.documents.count { it.id == categoryId })
    }
}
