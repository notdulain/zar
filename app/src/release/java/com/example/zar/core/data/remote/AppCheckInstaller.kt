package com.example.zar.core.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Release builds attest with Play Integrity only. The debug provider does not exist in this variant. */
object AppCheckInstaller {
    fun install(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return // built without google-services.json
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
