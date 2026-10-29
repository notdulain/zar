package com.example.zar.features.catchup

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.zar.features.catchup.data.CatchUpReminderWorker
import com.example.zar.features.catchup.data.DataStoreCatchUpPreferencesRepository
import com.example.zar.features.catchup.domain.model.ReminderSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the real WorkManager worker immediately; no Firebase records are read or written. */
class CatchUpReminderWorkerTest {
    @Test
    fun theWorkerPostsTheReminderAndQueuesOneSuccessorAndHonoursOff() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // From Android 13 a notification needs the runtime permission, and ReminderNotifier correctly posts nothing
        // without it. A fresh test install has not been granted it, so the fixture grants it as the person would.
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val preferences = DataStoreCatchUpPreferencesRepository(context)
        val original = preferences.observe().first()
        val work = WorkManager.getInstance(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        suspend fun runWorker() {
            val request = OneTimeWorkRequestBuilder<CatchUpReminderWorker>().build()
            work.enqueue(request).result.get(10, TimeUnit.SECONDS)
            val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                val info = work.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)
                if (info?.state?.isFinished == true) {
                    assertEquals(WorkInfo.State.SUCCEEDED, info.state)
                    return
                }
                android.os.SystemClock.sleep(100)
            }
            error("Reminder worker did not finish")
        }
        try {
            notifications.cancel(38)
            preferences.saveReminder(ReminderSettings())
            runWorker()
            val notification = notifications.activeNotifications.single { it.id == 38 }.notification
            assertEquals("Weekly catch-up", notification.extras.getString("android.title"))
            assertNotNull(notification.contentIntent)
            val pending = work.getWorkInfosForUniqueWork("catch-up-reminder").get(10, TimeUnit.SECONDS)
                .filter { !it.state.isFinished }
            assertEquals(1, pending.size)
            assertTrue(pending.single().nextScheduleTimeMillis > System.currentTimeMillis())
            notifications.cancel(38)
            preferences.saveReminder(ReminderSettings(enabled = false))
            runWorker()
            assertFalse(notifications.activeNotifications.any { it.id == 38 })
        } finally {
            preferences.saveReminder(original.reminder)
            notifications.cancel(38)
            com.example.zar.features.catchup.data.WorkManagerReminderScheduler(context)
                .schedule(com.example.zar.features.catchup.domain.ReminderSchedule.next(original.reminder, java.time.Instant.now()))
        }
    }
}
