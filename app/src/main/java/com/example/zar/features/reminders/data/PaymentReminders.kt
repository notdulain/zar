package com.example.zar.features.reminders.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.zar.MainActivity
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.features.auth.domain.repository.SignOutHandler
import com.example.zar.features.reminders.domain.DueReminder
import com.example.zar.features.reminders.domain.MarkRemindersShownUseCase
import com.example.zar.features.reminders.domain.PaymentReminderScheduler
import com.example.zar.features.reminders.domain.PlanRemindersUseCase
import com.example.zar.features.reminders.domain.ReminderKind
import com.example.zar.features.reminders.domain.ReminderPlanner
import com.example.zar.features.reminders.domain.ReminderPreferences
import com.example.zar.features.reminders.domain.ReminderPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.reminderStore by preferencesDataStore(name = "payment_reminders")

/** Which reminders were shown, and the overdue-milestone switch. Small and per phone, so not synced. */
@Singleton
class DataStoreReminderPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderPreferencesRepository {

    override fun observe(): Flow<ReminderPreferences> = context.reminderStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> ReminderPreferences(prefs[NOTIFIED].orEmpty(), prefs[MILESTONE_ALERTS] ?: true) }

    override suspend fun markNotified(keys: Set<String>, today: LocalDate) {
        context.reminderStore.edit { prefs -> prefs[NOTIFIED] = ReminderPlanner.prune(prefs[NOTIFIED].orEmpty() + keys, today) }
    }

    override suspend fun setOverdueMilestoneAlerts(enabled: Boolean) {
        context.reminderStore.edit { it[MILESTONE_ALERTS] = enabled }
    }

    override suspend fun clear() {
        context.reminderStore.edit { it.clear() }
    }

    private companion object {
        val NOTIFIED = stringSetPreferencesKey("notified_keys")
        val MILESTONE_ALERTS = booleanPreferencesKey("overdue_milestone_alerts")
    }
}

/**
 * One daily check (unique, kept if it already exists) plus an on-demand check after a setting
 * changes. Kept apart from the weekly catch-up reminder's job and channel.
 */
@Singleton
class WorkManagerPaymentReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : PaymentReminderScheduler {

    override fun ensureScheduled() {
        val now = ZonedDateTime.now(ZarTime.ZONE)
        var next = now.with(CHECK_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<PaymentReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun checkNow() {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<PaymentReminderWorker>().build())
    }

    override fun cancel() {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(DAILY_WORK)
            cancelUniqueWork(NOW_WORK)
        }
    }

    private companion object {
        const val DAILY_WORK = "payment-reminders"
        const val NOW_WORK = "payment-reminders-now"
        // Morning, so it doesn't land on top of the evening catch-up reminder.
        val CHECK_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

/** Shows today's bill and overdue-milestone reminders, each only once. */
class PaymentReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun planPaymentReminders(): PlanRemindersUseCase
        fun markPaymentRemindersShown(): MarkRemindersShownUseCase
        fun paymentReminderNotifier(): PaymentReminderNotifier
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        // Notifications refused or turned off: show nothing and don't pretend anything was shown.
        if (!deps.paymentReminderNotifier().canNotify()) return Result.success()
        val today = ZarTime.today()
        val reminders = runCatching { deps.planPaymentReminders()(today) }.getOrElse { return Result.retry() }
        reminders.forEach(deps.paymentReminderNotifier()::show)
        deps.markPaymentRemindersShown()(reminders, today)
        return Result.success()
    }
}

@Singleton
class PaymentReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    // Only called after canNotify().
    @SuppressLint("MissingPermission")
    fun show(reminder: DueReminder) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.payment_reminder_channel_name))
                .setDescription(context.getString(R.string.payment_reminder_channel_description))
                .build(),
        )
        val (title, body) = text(reminder)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, REQUEST_CODE, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // The lock screen shows only a generic line; names and amounts appear once unlocked.
        val public = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.payment_reminder_public_title))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // The ID comes from the reminder's key, so posting it again replaces rather than duplicates.
        manager.notify(reminder.key.hashCode(), notification)
    }

    private fun text(reminder: DueReminder): Pair<String, String> {
        val amount = reminder.amount.formatWithCode()
        val date = reminder.dueDate.format(DATE)
        return when (reminder.kind) {
            ReminderKind.BILL_DUE -> {
                val title = when (reminder.daysUntilDue) {
                    0 -> context.getString(R.string.payment_reminder_bill_today, reminder.name)
                    1 -> context.getString(R.string.payment_reminder_bill_tomorrow, reminder.name)
                    else -> context.getString(R.string.payment_reminder_bill_days, reminder.name, reminder.daysUntilDue)
                }
                title to context.getString(R.string.payment_reminder_bill_body, amount, date)
            }
            ReminderKind.MILESTONE_OVERDUE ->
                context.getString(R.string.payment_reminder_milestone_title, reminder.group.orEmpty(), reminder.name) to
                    context.getString(R.string.payment_reminder_milestone_body, amount, date)
        }
    }

    private companion object {
        const val CHANNEL_ID = "payment_reminders"
        const val REQUEST_CODE = 30
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    }
}

/** Reminder history belongs to the person who signed out. */
class ResetRemindersOnSignOut @Inject constructor(
    private val preferences: ReminderPreferencesRepository,
) : SignOutHandler {
    override suspend fun onSignOut() {
        preferences.clear()
    }
}
