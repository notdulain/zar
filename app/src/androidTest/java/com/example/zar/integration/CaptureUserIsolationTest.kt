package com.example.zar.integration

import com.example.zar.features.accounts.data.repository.FirestoreAccountRepository
import com.example.zar.features.capture.domain.usecase.ObserveCaptureAccountsUseCase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.data.remote.FirebaseUserSessionProvider
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.data.remote.firestore.FirestoreDraftRepository
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.auth.EmulatorFirebase
import com.example.zar.features.auth.IsolatedFirebaseApp
import com.example.zar.features.auth.step
import com.example.zar.features.capture.data.auto.CaptureStagingDatabase
import com.example.zar.features.capture.data.auto.RoomSmsStagingRepository
import com.example.zar.features.capture.data.auto.SmsCipher
import com.example.zar.features.capture.domain.CapturedDraftRules
import com.example.zar.features.capture.domain.auto.AutoCapturePreferences
import com.example.zar.features.capture.domain.auto.AutoCaptureScheduler
import com.example.zar.features.capture.domain.auto.ObserveAutoCaptureUseCase
import com.example.zar.features.capture.domain.auto.ProcessStagedSmsUseCase
import com.example.zar.features.capture.domain.auto.StageIncomingSmsUseCase
import com.example.zar.features.capture.domain.auto.StageOutcome
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.example.zar.features.capture.domain.usecase.CaptureTextUseCase
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * SCRUM-21 / D08: two real users on one phone, queued capture, sign-out, user switching and a controlled mid-flight
 * switch. Production [StageIncomingSmsUseCase] and [ProcessStagedSmsUseCase], the production encrypted Room staging
 * (Android Keystore AES-GCM) and the production Firestore repositories against the local emulators (project
 * demo-zar). Server contents are read with the emulator's rules-bypassing admin token so absence is proven, not
 * assumed. Synthetic users and one synthetic bank message only.
 */
class CaptureUserIsolationTest {

    private val sms = "Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111."
    private val received = Instant.parse("2026-10-11T05:19:00Z")
    private val draftId = CapturedDraftRules.draftIdFor(sms).value
    private val password = "d08-password-1"

    private lateinit var client: IsolatedFirebaseApp
    private lateinit var database: CaptureStagingDatabase
    private lateinit var staging: RoomSmsStagingRepository
    private val dbName = "d08-staging-${UUID.randomUUID()}.db"

    @Before fun setUp() {
        EmulatorFirebase.requireEmulators()
        client = IsolatedFirebaseApp.open("d08-capture-${UUID.randomUUID()}")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)
        database = Room.databaseBuilder(context, CaptureStagingDatabase::class.java, dbName).build()
        staging = RoomSmsStagingRepository(database.stagedSms(), SmsCipher())
    }

    @After fun tearDown() {
        database.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
        client.close()
    }

    private class Preferences : AutoCapturePreferences {
        val enabled = MutableStateFlow<UserId?>(null)
        override fun observeEnabledFor(): Flow<UserId?> = enabled
        override suspend fun enabledFor(): UserId? = enabled.value
        override suspend fun setEnabledFor(owner: UserId?) { enabled.value = owner }
    }

    private class Scheduler : AutoCaptureScheduler {
        var processSoon = 0
        override fun processSoon() { processSoon++ }
        override fun ensurePeriodic() = Unit
        override fun cancelPending() = Unit
    }

    /** A session whose [beforeRead] hook can change the signed-in user right before the n-th read. */
    private class RacingSession(private val real: UserSessionProvider, private val beforeRead: (Int) -> Unit) : UserSessionProvider {
        var reads = 0
        override fun currentUserId(): UserId? { reads++; beforeRead(reads); return real.currentUserId() }
        override fun observeUserId() = real.observeUserId()
    }

    private class Person(val email: String, var uid: String = "")

    private suspend fun newPerson(label: String): Person {
        val person = Person("d08-$label-${UUID.randomUUID().toString().take(8)}@zar.test")
        person.uid = client.signInOrCreate(person.email, password)
        client.auth.signOut()
        return person
    }

    private suspend fun signIn(person: Person) {
        step("sign in ${person.email}") { client.auth.signInWithEmailAndPassword(person.email, password).await() }
        assertEquals(person.uid, client.auth.currentUser?.uid)
    }

    private val realSession get() = FirebaseUserSessionProvider(client.auth)

    private fun process(session: UserSessionProvider): ProcessStagedSmsUseCase =
        ProcessStagedSmsUseCase(session, staging, CaptureTextUseCase(BankSmsParser(), FirestoreDraftRepository(client.db, session), ObserveCaptureAccountsUseCase(FirestoreAccountRepository(client.db, session))))

    private fun stageUseCase(prefs: Preferences, scheduler: Scheduler) =
        StageIncomingSmsUseCase(BankSmsParser(), realSession, prefs, staging, scheduler)

    private suspend fun serverDrafts(uid: String) = EmulatorAdmin.documents(uid, FirestoreCollections.DRAFTS)

    private fun assertNoRawText(json: String) {
        for (fragment in listOf("Dear Cardholder", "has been authorised", "debit card ending", "\"rawText\"")) {
            assertFalse("raw message text ($fragment) reached the server", json.contains(fragment))
        }
    }

    /**
     * The size a screen would settle on: the listener's first emission can come from an empty cache before the server
     * snapshot arrives, so keep listening briefly and take the last value.
     */
    private suspend fun <T> settledSize(open: () -> Flow<List<T>>): Int {
        var last = -1
        kotlinx.coroutines.withTimeoutOrNull(2_000) { open().collect { last = it.size } }
        check(last >= 0) { "the listener never emitted" }
        return last
    }

    /** What the signed-in user sees: drafts, ledger and the capture queue. */
    private suspend fun visibleToSignedInUser(): Triple<Int, Int, Int> {
        val session = realSession
        val owner = checkNotNull(session.currentUserId())
        val drafts = settledSize { FirestoreDraftRepository(client.db, session).observeDrafts() }
        val ledger = settledSize { FirestoreLedgerRepository(client.db, session).observeLedger() }
        val queue = step("visible queue") {
            ObserveAutoCaptureUseCase(session, Preferences(), staging).invoke().first().queue.waiting
        }
        assertEquals(owner, session.currentUserId())
        return Triple(drafts, ledger, queue)
    }

    @Test
    fun aQueuedMessageStaysWithItsOwnerWhileAnotherUserSignsInAndIsProcessedOnlyWhenTheOwnerReturns() = runBlocking {
        val a = newPerson("a")
        val b = newPerson("b")
        val prefs = Preferences()
        val scheduler = Scheduler()

        // A turns capture on and a bank message arrives: it is queued encrypted on the phone.
        signIn(a)
        prefs.enabled.value = UserId(a.uid)
        assertEquals(StageOutcome.STAGED, stageUseCase(prefs, scheduler)(sms, received))
        assertEquals(1, scheduler.processSoon)
        assertEquals(1, staging.waiting(UserId(a.uid)).size)

        // A signs out; a message arriving now is not stored for anyone.
        client.auth.signOut()
        assertEquals(StageOutcome.SIGNED_OUT, stageUseCase(prefs, scheduler)(sms, received.plusSeconds(5)))
        assertEquals(1, database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM staged_sms").use { it.moveToFirst(); it.getInt(0) })

        // Nobody signed in: processing is paused and uploads nothing.
        val paused = process(realSession)(received.plusSeconds(10))
        assertTrue(paused.paused)
        assertEquals(0, paused.processed)
        assertTrue(serverDrafts(a.uid).isEmpty())

        // B signs in. B sees nothing of A, and B's own incoming message is refused because A owns capture.
        signIn(b)
        assertEquals(Triple(0, 0, 0), visibleToSignedInUser())
        assertEquals(StageOutcome.OFF, stageUseCase(prefs, scheduler)(sms, received.plusSeconds(15)))
        val bPass = process(realSession)(received.plusSeconds(20))
        assertEquals(0, bPass.processed)
        assertEquals(0, bPass.left)
        assertTrue("nothing reached B's drafts", serverDrafts(b.uid).isEmpty())
        assertTrue("nothing reached A's drafts", serverDrafts(a.uid).isEmpty())
        assertEquals("A's message is still queued, still bound to A", 1, staging.waiting(UserId(a.uid)).size)
        assertTrue(staging.waiting(UserId(b.uid)).isEmpty())
        // B cannot read A's collections either.
        try {
            step("B reads A's drafts") { client.db.collection("users").document(a.uid).collection("drafts").get(Source.SERVER).await() }
            fail("B could read A's drafts")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // A signs back in and the pass resumes: exactly one structured draft, in A's account only.
        client.auth.signOut()
        signIn(a)
        assertEquals(Triple(0, 0, 1), visibleToSignedInUser())
        val resumed = process(realSession)(received.plusSeconds(30))
        assertEquals(1, resumed.processed)
        assertFalse(resumed.paused)
        val drafts = serverDrafts(a.uid)
        assertEquals(setOf(draftId), drafts.keys)
        drafts.values.forEach(::assertNoRawText)
        assertTrue(serverDrafts(b.uid).isEmpty())
        assertTrue("the message text is gone from the phone once processed", staging.waiting(UserId(a.uid)).isEmpty())
        val file = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(dbName)
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        assertFalse("plain text never reaches the staging file", file.readBytes().toString(Charsets.ISO_8859_1).contains("CAFE SEVENTY THREE"))

        // Replays create nothing: the same message staged and processed again maps to the same draft.
        assertEquals(StageOutcome.STAGED, stageUseCase(prefs, scheduler)(sms, received.plusSeconds(40)))
        assertEquals(1, process(realSession)(received.plusSeconds(50)).processed)
        assertEquals(setOf(draftId), serverDrafts(a.uid).keys)
        assertEquals(Triple(1, 0, 0), visibleToSignedInUser())

        // And B, signing in again, still sees none of it.
        client.auth.signOut()
        signIn(b)
        assertEquals(Triple(0, 0, 0), visibleToSignedInUser())
        assertTrue(serverDrafts(b.uid).isEmpty())
    }

    /**
     * Switches the signed-in user right before each possible read of the session during a processing pass, one run
     * per read: before the pass starts, between its ownership check and the repository's own read, inside the
     * repository's transaction, and after it. In every run A's message must never become B's data and must never be
     * lost; A's later resume must leave exactly one draft in A's account.
     */
    @Test
    fun aUserSwitchAtAnyPointOfAProcessingPassNeverPublishesAsMessageToBAndNeverLosesIt() = runBlocking {
        val prefs = Preferences()
        val scheduler = Scheduler()

        // Dry run (no switch) to learn how many session reads one pass makes, so no window is left unexercised.
        val dryA = newPerson("dry")
        signIn(dryA)
        prefs.enabled.value = UserId(dryA.uid)
        stageUseCase(prefs, scheduler)(sms, received)
        val counting = RacingSession(realSession) {}
        process(counting)(received.plusSeconds(1))
        val reads = counting.reads
        assertTrue("expected the pass to read the session several times, saw $reads", reads >= 5)
        assertEquals(setOf(draftId), serverDrafts(dryA.uid).keys)
        client.auth.signOut()

        val failures = mutableListOf<String>()
        for (switchBefore in 1..reads + 1) {
            val a = newPerson("ra$switchBefore")
            val b = newPerson("rb$switchBefore")
            signIn(a)
            prefs.enabled.value = UserId(a.uid)
            database.openHelper.writableDatabase.execSQL("DELETE FROM staged_sms")
            assertEquals(StageOutcome.STAGED, stageUseCase(prefs, scheduler)(sms, received))

            val racing = RacingSession(realSession) { read ->
                if (read == switchBefore) {
                    client.auth.signOut()
                    Tasks.await(client.auth.signInWithEmailAndPassword(b.email, password), 15, TimeUnit.SECONDS)
                }
            }
            val outcome = runCatching { process(racing)(received.plusSeconds(2)) }

            val inB = serverDrafts(b.uid)
            val inA = serverDrafts(a.uid)
            val queued = staging.waiting(UserId(a.uid)).size
            if (inB.isNotEmpty()) failures += "switch before read $switchBefore: A's draft was published into B's account (${inB.keys})"
            if (inA.isEmpty() && queued == 0) failures += "switch before read $switchBefore: A's message was lost (not on the server, not queued)"
            if (queued > 0 && outcome.getOrNull()?.processed == 1 && inA.isEmpty()) failures += "switch before read $switchBefore: reported processed but nothing saved for A"
            (inA.values + inB.values).forEach { json ->
                if (listOf("Dear Cardholder", "has been authorised", "\"rawText\"").any { it in json }) failures += "switch before read $switchBefore: raw text uploaded"
            }

            // B is signed in now (or still A for the late switches): whoever it is sees only their own data.
            if (client.auth.currentUser?.uid == b.uid) {
                val (drafts, ledger, queue) = visibleToSignedInUser()
                if (drafts != inB.size || ledger != 0 || queue != 0) failures += "switch before read $switchBefore: B sees drafts=$drafts ledger=$ledger queue=$queue"
            }

            // A comes back and resumes: exactly one draft for A, none for B, nothing left queued.
            client.auth.signOut()
            signIn(a)
            process(realSession)(received.plusSeconds(3))
            val finalA = serverDrafts(a.uid)
            if (finalA.keys != setOf(draftId)) failures += "switch before read $switchBefore: after resume A has ${finalA.keys}"
            if (serverDrafts(b.uid).isNotEmpty()) failures += "switch before read $switchBefore: B ended with a draft"
            if (staging.waiting(UserId(a.uid)).isNotEmpty()) failures += "switch before read $switchBefore: message still queued after resume"
            client.auth.signOut()
        }
        assertTrue("Cross-user or lost capture:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
