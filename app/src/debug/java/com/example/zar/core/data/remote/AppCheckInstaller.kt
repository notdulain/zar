package com.example.zar.core.data.remote

import android.content.Context
import com.example.zar.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the App Check debug provider so emulators and developer devices can call the enforced service.
 * On first run it logs a debug token (Logcat tag DebugAppCheckProvider); register it under Firebase console >
 * App Check > Manage debug tokens. Never commit or share that token.
 */
object AppCheckInstaller {
    fun install(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return // built without google-services.json
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            // Local emulator traffic must not wait for, or exchange tokens with, the live attestation service.
            FirebaseAppCheck.getInstance().setTokenAutoRefreshEnabled(false)
            return
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
