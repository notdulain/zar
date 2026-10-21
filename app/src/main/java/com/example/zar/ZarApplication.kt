package com.example.zar

import android.app.Application
import com.example.zar.core.data.remote.AppCheckInstaller
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before any Firebase call, so every callable request carries an App Check token.
        AppCheckInstaller.install(this)
    }
}
