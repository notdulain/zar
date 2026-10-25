package com.example.zar

import com.example.zar.core.presentation.privacy.syncAmountMask
import com.example.zar.core.domain.repository.AmountPrivacyRepository
import android.app.Application
import com.example.zar.core.data.remote.AppCheckInstaller
import com.example.zar.features.capture.domain.auto.ResumeAutoCaptureUseCase
import com.example.zar.features.catchup.domain.usecase.ScheduleReminderUseCase
import com.example.zar.features.reminders.domain.PaymentReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ZarApplication : Application() {

    @Inject lateinit var scheduleReminder: ScheduleReminderUseCase
    @Inject lateinit var paymentReminders: PaymentReminderScheduler
    @Inject lateinit var resumeAutoCapture: ResumeAutoCaptureUseCase
    @Inject lateinit var amountPrivacy: AmountPrivacyRepository

    override fun onCreate() {
        super.onCreate()
        // Read the hide-amounts choice before anything is drawn, so a hidden amount is never shown first.
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).syncAmountMask(amountPrivacy)
        AppCheckInstaller.install(this)
        // Restore the weekly reminder without delaying startup.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { scheduleReminder() } }
        // The daily bill and milestone check; kept if it already exists, so it is never doubled.
        runCatching { paymentReminders.ensureScheduled() }
        // Automatic SMS capture picks up again whenever the person who turned it on is signed in.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { resumeAutoCapture() } }
    }
}
