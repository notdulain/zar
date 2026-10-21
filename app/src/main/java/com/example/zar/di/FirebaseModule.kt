package com.example.zar.di

import com.example.zar.BuildConfig
import com.example.zar.features.quicknote.data.remote.FirebaseQuickNoteDraftTransport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Firebase SDK entry points for data-layer classes. Only inject these into `data` packages.
// They require app/google-services.json at build time.
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    // The Android emulator reaches the host machine's Firebase Emulator Suite through 10.0.2.2.
    private const val EMULATOR_HOST = "10.0.2.2"

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance().apply {
        if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(EMULATOR_HOST, 9099)
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(EMULATOR_HOST, 8080)
    }

    // The quick-note draft callable lives in asia-south1 (D05). Emulator builds use the Functions emulator.
    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance(FirebaseQuickNoteDraftTransport.REGION).apply {
            if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(EMULATOR_HOST, 5001)
        }
}
