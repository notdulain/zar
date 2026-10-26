package com.example.zar.features.allowance

import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.auth.IsolatedFirebaseApp
import com.example.zar.features.allowance.data.repository.FirestoreAllowanceRepository
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.model.AllowanceSyncState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.Assert.*

/** Actual Android repository with local Auth/Firestore emulators and the repository rules. */
class AllowanceRepositoryTest {
    companion object {
        private lateinit var client: IsolatedFirebaseApp
        private lateinit var db: FirebaseFirestore
        private var uid: UserId = UserId("not-connected")
        @JvmStatic @BeforeClass fun connect() = runBlocking {
            client = IsolatedFirebaseApp.open("allowance-${UUID.randomUUID()}")
            uid = UserId(checkNotNull(client.auth.signInAnonymously().await().user).uid)
            db = client.db
        }
        @JvmStatic @AfterClass fun disconnect() { client.close() }
    }
    private val users by lazy { MutableStateFlow<UserId?>(uid) }
    private val session = object : UserSessionProvider {
        override fun currentUserId() = users.value
        override fun observeUserId() = users
    }
    private fun repository() = FirestoreAllowanceRepository(db, session)
    private fun setting(amount: Long = 8_000) = AllowanceSettings(true, FiatMoney(amount * 100, Currencies.LKR), setOf("groceries"))
    private fun document() = db.collection("users").document(uid.value).collection("allowance").document("weekly")

    @Test fun savesSettingsWithoutWritingAnyLedgerEntry() = runBlocking {
        val repo = repository(); val settings = setting()
        repo.saveSettings(settings)
        assertEquals(settings, withTimeout(10_000) { repo.observeSettings().first { it == settings } })
        assertEquals(AllowanceSyncState.SAVED, withTimeout(10_000) { repo.observeSyncState().first { it == AllowanceSyncState.SAVED } })
        assertEquals(800_000L, (document().get(Source.SERVER).await().get("amount") as Map<*, *>)["minor"])
        assertTrue(db.collection("users").document(uid.value).collection("transactions").get(Source.SERVER).await().isEmpty)
    }

    @Test fun offlineChoiceIsQueuedThenAcknowledgedOnReconnect() = runBlocking {
        val repo = repository(); repo.saveSettings(setting())
        val offline = setting(9_000)
        try {
            db.disableNetwork().await(); repo.saveSettings(offline)
            assertEquals(AllowanceSyncState.QUEUED, repo.observeSyncState().first())
            assertEquals(offline, withTimeout(5_000) { repo.observeSettings().first { it == offline } })
        } finally { db.enableNetwork().await() }
        assertEquals(AllowanceSyncState.SAVED, withTimeout(15_000) { repo.observeSyncState().first { it == AllowanceSyncState.SAVED } })
        assertEquals(900_000L, (document().get(Source.SERVER).await().get("amount") as Map<*, *>)["minor"])
    }

    @Test fun immediateServerRejectionIsReturnedAndDoesNotLeakAcrossUsers() = runBlocking {
        val repo = repository(); repo.saveSettings(setting())
        val invalid = setting().copy(categoryIds = setOf("")) // The actual server rule rejects this.
        assertTrue(runCatching { repo.saveSettings(invalid) }.isFailure)
        assertEquals(AllowanceSyncState.FAILED, withTimeout(5_000) { repo.observeSyncState().first { it == AllowanceSyncState.FAILED } })
        users.value = null
        assertEquals(AllowanceSyncState.SAVED, repo.observeSyncState().first())
        assertTrue(runCatching { repo.saveSettings(setting()) }.isFailure)
    }

    @Test fun aQueuedWriteRejectedAfterReconnectKeepsFailureVisibleAfterRollback() = runBlocking {
        val repo = repository(); val original = setting(); repo.saveSettings(original)
        try {
            db.disableNetwork().await(); repo.saveSettings(original.copy(categoryIds = setOf("")))
            assertEquals(AllowanceSyncState.QUEUED, repo.observeSyncState().first())
        } finally { db.enableNetwork().await() }
        assertEquals(AllowanceSyncState.FAILED, withTimeout(15_000) { repo.observeSyncState().first { it == AllowanceSyncState.FAILED } })
        assertEquals(original, withTimeout(5_000) { repo.observeSettings().first { it == original } })
        assertEquals(AllowanceSyncState.FAILED, repo.observeSyncState().first())
    }
}
