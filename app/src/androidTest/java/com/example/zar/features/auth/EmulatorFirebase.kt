package com.example.zar.features.auth

import com.example.zar.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import org.junit.Assume.assumeTrue

/**
 * Firebase instances wired to the local Emulator Suite. These tests never touch the live project:
 * run them with `-Pzar.useEmulators=true` while `firebase emulators:start --only auth,firestore` runs.
 */
object EmulatorFirebase {
    fun requireEmulators() = assumeTrue("Run with -Pzar.useEmulators=true", BuildConfig.USE_FIREBASE_EMULATORS)

    // The app's Hilt graph configures the default FirebaseAuth/FirebaseFirestore for the emulators exactly once, and
    // `useEmulator` throws if it is called again on an instance already in use. Tests that only need a repository and a
    // user therefore get a client of their own (see IsolatedFirebaseApp) and never touch the app's default instances,
    // whichever order the classes run in or whether the app has started in the same process.
    private val client: IsolatedFirebaseApp by lazy { IsolatedFirebaseApp.open("emulator-firebase-${UUID.randomUUID()}") }
    val auth: FirebaseAuth get() = client.auth
    val firestore: FirebaseFirestore get() = client.db

    fun newEmail(): String = "test-${UUID.randomUUID().toString().take(8)}@zar.test"
}
