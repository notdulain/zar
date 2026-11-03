package com.example.zar.core.data.remote

import android.content.Context
import com.example.zar.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * The Macrobenchmark target behaves like release: Play Integrity only. This variant is profiled on an emulator and is
 * never shipped; Firebase calls are not needed for the timed journeys (which stay on the sign-in screen or a seeded
 * emulator session).
 */
object AppCheckInstaller {
    fun install(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return // built without google-services.json
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            // Local emulator traffic must not wait for, or exchange tokens with, the live attestation service.
            FirebaseAppCheck.getInstance().setTokenAutoRefreshEnabled(false)
            return
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
