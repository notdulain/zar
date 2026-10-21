package com.example.zar

import android.app.Application
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
        // Arrange the weekly catch-up reminder from the saved settings, in case it was lost (reinstall, cleared data).
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { scheduleReminder() } }
    }
}
