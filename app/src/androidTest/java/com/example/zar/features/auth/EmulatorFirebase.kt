package com.example.zar.features.auth

import com.example.zar.BuildConfig
import com.example.zar.di.FirebaseModule
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

    // The app may already have configured these singletons through Hilt; configuring twice throws.
    val auth: FirebaseAuth by lazy { runCatching { FirebaseModule.provideFirebaseAuth() }.getOrElse { FirebaseAuth.getInstance() } }
    val firestore: FirebaseFirestore by lazy {
        runCatching { FirebaseModule.provideFirebaseFirestore() }.getOrElse { FirebaseFirestore.getInstance() }
    }

    fun newEmail(): String = "test-${UUID.randomUUID().toString().take(8)}@zar.test"
}
