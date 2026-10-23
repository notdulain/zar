package com.example.zar.features.capture.data.auto

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.zar.core.domain.model.UserId
import com.example.zar.features.capture.domain.auto.AutoCapturePreferences
import com.example.zar.features.capture.domain.auto.AutoCaptureScheduler
import com.example.zar.features.capture.domain.auto.ProcessStagedSmsUseCase
import com.example.zar.features.capture.domain.auto.StageIncomingSmsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.autoCaptureStore by preferencesDataStore(name = "auto_sms_capture")

/**
 * Who turned capture on, on this phone. The SMS receiver is only enabled while someone has, so
 * Zar isn't even told about new messages otherwise.
 */
@Singleton
class DataStoreAutoCapturePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutoCapturePreferences {

    override fun observeEnabledFor(): Flow<UserId?> = context.autoCaptureStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[ENABLED_FOR]?.let(::UserId) }

    override suspend fun enabledFor(): UserId? = observeEnabledFor().first()

    override suspend fun setEnabledFor(owner: UserId?) {
        context.autoCaptureStore.edit { prefs -> if (owner == null) prefs.remove(ENABLED_FOR) else prefs[ENABLED_FOR] = owner.value }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, SmsCaptureReceiver::class.java),
            if (owner == null) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private companion object {
        val ENABLED_FOR = stringPreferencesKey("enabled_for")
    }
}

/**
 * A pass soon after each message (once online, retried with backoff), plus a regular pass that
 * retries leftovers and expires old messages even offline.
 */
@Singleton
class WorkManagerAutoCaptureScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutoCaptureScheduler {

    override fun processSoon() {
        val request = OneTimeWorkRequestBuilder<AutoCaptureWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Queued after a pass that is already running, so a message arriving mid-pass isn't missed.
        WorkManager.getInstance(context).enqueueUniqueWork(SOON_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<AutoCaptureWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(REGULAR_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelPending() {
        WorkManager.getInstance(context).cancelUniqueWork(SOON_WORK)
    }

    private companion object {
        const val SOON_WORK = "sms-capture-soon"
        const val REGULAR_WORK = "sms-capture-regular"
    }
}

/** Turns staged messages into drafts for the signed-in user and expires old ones. */
class AutoCaptureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun processStagedSms(): ProcessStagedSmsUseCase
    }

    override suspend fun doWork(): Result {
        val process = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).processStagedSms()
        val result = runCatching { process(Instant.now()) }.getOrElse { return Result.retry() }
        // Paused (signed out) isn't a failure: the messages wait for their owner or expire.
        return if (result.left > 0 && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/**
 * Receives each new SMS while capture is on. Only newly received messages arrive here; Zar
 * never reads the inbox, so past messages are never scanned. The text is handed straight to
 * [StageIncomingSmsUseCase], which keeps it (encrypted) only if it's a tested bank format.
 */
class SmsCaptureReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun stageIncomingSms(): StageIncomingSmsUseCase
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty().filterNotNull()
        if (parts.isEmpty()) return
        // A long message arrives in parts; join them per sender.
        val messages = parts.groupBy { it.displayOriginatingAddress }.values.map { group ->
            group.joinToString("") { it.displayMessageBody.orEmpty() } to
                Instant.ofEpochMilli(minOf(group.minOf { it.timestampMillis }, System.currentTimeMillis()))
        }
        val stage = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).stageIncomingSms()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                messages.forEach { (text, receivedAt) -> runCatching { stage(text, receivedAt) } }
            } finally {
                pending.finish()
            }
        }
    }
}
