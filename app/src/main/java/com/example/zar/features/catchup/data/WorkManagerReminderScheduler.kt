package com.example.zar.features.catchup.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.zar.features.catchup.domain.repository.CatchUpPreferencesRepository
import com.example.zar.features.catchup.domain.repository.ReminderScheduler
import com.example.zar.features.catchup.domain.usecase.ScheduleReminderUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Arranges the reminder as one pending job. Android decides the exact moment the job runs, so the reminder can
 * arrive a little after the chosen time. Scheduling again replaces the pending job.
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    override fun schedule(at: ZonedDateTime?) {
        val work = WorkManager.getInstance(context)
        if (at == null) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = Duration.between(Instant.now(), at.toInstant()).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<CatchUpReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "catch-up-reminder"
    }
}

/** Shows the reminder, then arranges the next one for the following week. */
class CatchUpReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun notifier(): ReminderNotifier
        fun preferences(): CatchUpPreferencesRepository
        fun scheduleReminder(): ScheduleReminderUseCase
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        // Off by the time it fires (for example turned off while the job waited): show nothing.
        if (deps.preferences().observe().first().reminder.enabled) deps.notifier().show()
        deps.scheduleReminder()()
        return Result.success()
    }
}
