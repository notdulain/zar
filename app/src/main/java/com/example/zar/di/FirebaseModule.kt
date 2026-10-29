package com.example.zar.di

import com.example.zar.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.appcheck.FirebaseAppCheck
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

    // Only read when emulators are on (debug and benchmark builds): 10.0.2.2 is the Android emulator's route to the
    // host machine's Firebase Emulator Suite; a physical phone uses 127.0.0.1 through `adb reverse` (zar.emulatorHost).
    private val EMULATOR_HOST = BuildConfig.EMULATOR_HOST

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

    @Provides
    @Singleton
    fun provideFirebaseAppCheck(): FirebaseAppCheck = FirebaseAppCheck.getInstance()
}
