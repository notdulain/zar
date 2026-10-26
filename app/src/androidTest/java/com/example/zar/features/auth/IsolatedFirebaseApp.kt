package com.example.zar.features.auth

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * One Firebase client (app + Auth + Firestore) of its own, wired to the local emulators.
 *
 * Every [open] gets a fresh API key and application ID as well as its own app name. Two clients that used the same
 * fake API key made the second one fail in `FirebaseAuth.useEmulator` with "FirebaseApp was deleted", because Auth
 * shares its backend client per API key and the first client had already deleted its app; and the heartbeat DataStore
 * file is named from the app name and application ID, where two live DataStores crash the process. Fresh values per
 * open keep classes independent (so they can run in one instrumentation process) and let a deleted app's name be
 * opened again.
 *
 * Firestore's on-disk cache is keyed by the app name. Opening the same [name] again after [close] is therefore a
 * restarted client reading the same persistent cache. [firestorePort] may point at a closed port to start a client
 * that cannot reach the server, which makes the cache readable without the pending writes being sent.
 */
class IsolatedFirebaseApp private constructor(
    val name: String,
    private val app: FirebaseApp,
    val auth: FirebaseAuth,
    val db: FirebaseFirestore,
) {
    /** Signs in as [email], creating the account first if the emulator does not know it. Returns the uid. */
    suspend fun signInOrCreate(email: String, password: String): String {
        val user = runCatching { auth.signInWithEmailAndPassword(email, password).await().user }.getOrNull()
            ?: auth.createUserWithEmailAndPassword(email, password).await().user
        return checkNotNull(user) { "no user after sign-in" }.uid
    }

    /** Shuts the Firestore client down and deletes the app; the persistent cache stays on disk. */
    fun close() {
        runCatching { Tasks.await(db.terminate(), 10, TimeUnit.SECONDS) }
        app.delete()
    }

    companion object {
        /** The Android emulator's route to the host machine, where the Firebase emulators listen. */
        const val EMULATOR_HOST = "10.0.2.2"
        const val FIRESTORE_PORT = 8080
        const val AUTH_PORT = 9099
        /** Nothing listens here, so a client pointed at it stays offline. */
        const val UNREACHABLE_PORT = 1

        fun open(
            name: String = "isolated-${UUID.randomUUID()}",
            firestorePort: Int = FIRESTORE_PORT,
        ): IsolatedFirebaseApp {
            val app = FirebaseApp.initializeApp(
                InstrumentationRegistry.getInstrumentation().targetContext,
                FirebaseOptions.Builder().setProjectId("demo-zar").setApplicationId("1:123:android:${UUID.randomUUID().hashCode().toUInt()}")
                    .setApiKey("fake-emulator-key-${UUID.randomUUID()}").build(),
                name,
            )
            val auth = FirebaseAuth.getInstance(app).also { it.useEmulator(EMULATOR_HOST, AUTH_PORT) }
            val db = FirebaseFirestore.getInstance(app).also { it.useEmulator(EMULATOR_HOST, firestorePort) }
            return IsolatedFirebaseApp(name, app, auth, db)
        }
    }
}

/** Runs [block] for at most [millis] and, on a timeout, fails with the step's name instead of a bare hang. */
suspend fun <T> step(label: String, millis: Long = 15_000, block: suspend () -> T): T =
    try {
        withTimeout(millis) { block() }
    } catch (e: TimeoutCancellationException) {
        throw AssertionError("step \"$label\" did not finish within $millis ms", e)
    }
