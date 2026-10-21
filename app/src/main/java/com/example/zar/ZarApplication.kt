package com.example.zar

import android.app.Application
import com.example.zar.core.data.remote.AppCheckInstaller
import com.example.zar.features.catchup.domain.usecase.ScheduleReminderUseCase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ZarApplication : Application() {

    @Inject lateinit var scheduleReminder: ScheduleReminderUseCase

    override fun onCreate() {
        super.onCreate()
        AppCheckInstaller.install(this)
        // Restore the weekly reminder without delaying startup.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { scheduleReminder() } }
    }
}
