package com.example.zar.features.transactions

import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.auth.IsolatedFirebaseApp
import com.example.zar.features.auth.step
import com.example.zar.features.transactions.data.category.FirestoreExpenseCategoryRepository
import com.example.zar.features.transactions.domain.category.CategoryLabel
import com.example.zar.features.transactions.domain.category.CategoryNameRules
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.category.repository.CustomCategorySnapshot
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.google.firebase.firestore.Source
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * The real Android Firestore repository against the local Auth/Firestore emulators and the repository rules.
 * It exercises what the unit tests cannot: offline creation, reconnect, account switching and a
 * listener error that must degrade instead of dropping the user's own names.
 *
 * Run with `-Pzar.useEmulators=true` while `firebase emulators:start --only auth,firestore` runs. The emulator
 * host is `10.0.2.2` (the Android emulator's loopback), so this needs its own AVD and must not share one.
 * Every step of the offline test is time-bounded and names itself on a timeout.
 */
class ExpenseCategoryRepositoryTest {
    companion object {
        private lateinit var client: IsolatedFirebaseApp
        private val auth get() = client.auth
        private val db get() = client.db
        private var uid: UserId = UserId("not-connected")
        @JvmStatic @BeforeClass fun connect() { client = IsolatedFirebaseApp.open("category-${UUID.randomUUID()}") }
        @JvmStatic @AfterClass fun disconnect() { client.close() }
    }

    private val users = MutableStateFlow<UserId?>(null)
    private val session = object : UserSessionProvider {
        override fun currentUserId() = users.value
        override fun observeUserId() = users
    }

    /** A fresh signed-in user per test, so tests do not leak auth or catalog state into each other. */
    @Before fun signIn() = runBlocking {
        uid = UserId(checkNotNull(auth.signInAnonymously().await().user).uid)
        users.value = uid
    }

    private fun repository() = FirestoreExpenseCategoryRepository(db, session)
    private fun category(id: String, name: String) =
        CustomExpenseCategory(id, name, CategoryNameRules.keyOf(name), Instant.ofEpochMilli(1_790_000_000_000))
    private fun categories(uid: UserId) = db.collection("users").document(uid.value).collection("expenseCategories")
    private fun transactions(uid: UserId) = db.collection("users").document(uid.value).collection("transactions")
    private fun expense(id: String, categoryId: String) = mapOf(
        "id" to id, "accountId" to "bank", "type" to "EXPENSE",
        "amount" to mapOf("currency" to "LKR", "kind" to "FIAT", "digits" to 2, "minor" to 100_000L),
        "date" to "2026-10-10", "conversion" to mapOf("status" to "SAME"),
        "spendingClass" to "DISCRETIONARY", "source" to "MANUAL", "createdAt" to 1_790_000_000_000L,
        "categoryId" to categoryId,
    )
    private suspend fun snapshot(repo: FirestoreExpenseCategoryRepository, predicate: (CustomCategorySnapshot) -> Boolean) =
        withTimeout(15_000) { repo.observeCustomCategories().first(predicate) }

    /**
     * Local-first: while the network is off, the category and an expense that uses it are readable from the cache at
     * once (a second repository on the same client sees them), and reconnecting delivers both exactly once.
     * `set(...)` completes only on a server acknowledgement, so it is never awaited while offline; the restart across
     * a closed client and a dead process is in [OfflineRestartTest].
     */
    @Test fun offlineCreateThenExpenseIsLocalFirstAndReconnectDeliversThem() = runBlocking {
        val id = CustomCategoryIds.newId()
        val repo = repository()
        var expenseWrite: com.google.android.gms.tasks.Task<Void>? = null
        try {
            step("disableNetwork", 5_000) { db.disableNetwork().await() }
            step("create offline", 10_000) { repo.create(category(id, "Pets")) }
            // The write is local, so the catalog shows it at once and an expense can reference it offline.
            val offline = snapshot(repo) { it.categories.any { c -> c.id == id } }
            assertFalse(offline.isDegraded)
            expenseWrite = transactions(uid).document("e1").set(expense("e1", id))
            val cached = step("read expense from cache") { transactions(uid).document("e1").get(Source.CACHE).await() }
            assertTrue(cached.exists() && cached.metadata.hasPendingWrites())

            // A second repository on the same client reads the same local cache and still resolves the name.
            val reread = snapshot(repository()) { it.categories.any { c -> c.id == id } }
            val label = ExpenseCategoryCatalog(reread.categories).label(id)
            assertEquals("Pets", (label as CategoryLabel.Custom).name)
        } finally {
            step("enableNetwork", 10_000) { db.enableNetwork().await() }
        }
        // Reconnect: the acknowledgement arrives now, and the category and the expense reach the server once.
        step("expense acknowledged after reconnect", 20_000) { checkNotNull(expenseWrite).await() }
        step("pending writes flushed", 20_000) { db.waitForPendingWrites().await() }
        val serverCategories = step("server categories") { categories(uid).get(Source.SERVER).await() }
        assertEquals(1, serverCategories.documents.count { it.id == id })
        assertEquals("Pets", serverCategories.documents.first { it.id == id }.getString("name"))
        assertEquals(id, step("server expense") { transactions(uid).document("e1").get(Source.SERVER).await() }.getString("categoryId"))
    }

    @Test fun aRetryOfTheSameIdWritesOneDocumentAndADifferentWriteIsRefused() = runBlocking {
        val id = CustomCategoryIds.newId()
        val repo = repository()
        repo.create(category(id, "Pets"))
        repo.create(category(id, "Pets"))
        assertEquals(1, categories(uid).get(Source.SERVER).await().documents.count { it.id == id })
        // A category is never renamed: the rules refuse different data for the same id.
        assertTrue(runCatching { repo.create(category(id, "Dogs")) }.isFailure)
        assertEquals("Pets", categories(uid).document(id).get(Source.SERVER).await().getString("name"))
    }

    @Test fun aListenerErrorDegradesInsteadOfDroppingTheCatalogAndRecovers() = runBlocking {
        val id = CustomCategoryIds.newId()
        val repo = repository()
        repo.create(category(id, "Pets"))
        snapshot(repo) { it.categories.any { c -> c.id == id } }

        // Point at a scope this signed-in user cannot read: the listener fails and the flow must degrade, not end.
        users.value = UserId("someone-else-${UUID.randomUUID()}")
        assertTrue(snapshot(repo) { it.isDegraded }.isDegraded)

        // Back to the owner: the listener re-subscribes and the catalog recovers with the name intact.
        users.value = uid
        val recovered = snapshot(repo) { !it.isDegraded && it.categories.any { c -> c.id == id } }
        assertNotNull(ExpenseCategoryCatalog(recovered.categories).label(id))
    }

    @Test fun accountsAreIsolatedAndTheOwnersCatalogReturns() = runBlocking {
        val password = "test-password-1"
        val email = "cat-${UUID.randomUUID().toString().take(8)}@zar.test"
        val owner = UserId(checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user).uid)
        users.value = owner
        val id = CustomCategoryIds.newId()
        repository().create(category(id, "Pets"))
        snapshot(repository()) { it.categories.any { c -> c.id == id } }

        // A different account on the same device sees none of the owner's categories.
        auth.signOut()
        val otherEmail = "cat-${UUID.randomUUID().toString().take(8)}@zar.test"
        val other = UserId(checkNotNull(auth.createUserWithEmailAndPassword(otherEmail, password).await().user).uid)
        users.value = other
        assertTrue(snapshot(repository()) { true }.categories.isEmpty())
        // The other account may reuse the same name in its own catalog.
        val otherId = CustomCategoryIds.newId()
        repository().create(category(otherId, "Pets"))
        assertTrue(snapshot(repository()) { it.categories.any { c -> c.id == otherId } }.categories.any { it.id == otherId })
        // The owner's document is not readable under the other account's scope.
        assertTrue(runCatching { categories(owner).document(id).get(Source.SERVER).await() }.isFailure)

        // The owner signs back in and their catalog returns.
        auth.signOut()
        auth.signInWithEmailAndPassword(email, password).await()
        users.value = owner
        val restored = snapshot(repository()) { it.categories.any { c -> c.id == id } }
        assertEquals("Pets", (ExpenseCategoryCatalog(restored.categories).label(id) as CategoryLabel.Custom).name)
    }
}
