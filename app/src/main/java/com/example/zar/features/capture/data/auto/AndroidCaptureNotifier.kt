package com.example.zar.features.capture.data.auto

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.zar.MainActivity
import com.example.zar.R
import com.example.zar.core.navigation.LaunchTargets
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.features.capture.domain.auto.CaptureNotifier
import com.example.zar.features.capture.domain.auto.CapturedForReview
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One notification per message captured in the background; tapping it opens that draft's review. */
@Singleton
class AndroidCaptureNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : CaptureNotifier {

    @SuppressLint("MissingPermission") // Checked just below.
    override fun draftWaiting(capture: CapturedForReview) {
        val manager = NotificationManagerCompat.from(context)
        // Notifications refused or off: the draft still waits in Needs review.
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.capture_notify_channel_name))
                .setDescription(context.getString(R.string.capture_notify_channel_description))
                .build(),
        )
        val id = capture.draftId.value
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(LaunchTargets.EXTRA, LaunchTargets.CAPTURE_REVIEW)
            .putExtra(LaunchTargets.EXTRA_DRAFT_ID, id)
        // A request code per draft, so each notification opens its own draft.
        val tap = PendingIntent.getActivity(context, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val amount = capture.amount.formatWithCode()
        val title = capture.counterparty?.let { context.getString(R.string.capture_notify_title_at, amount, it) }
            ?: context.getString(R.string.capture_notify_title, amount)
        val body = capture.accountName?.let { context.getString(R.string.capture_notify_body_account, it) }
            ?: context.getString(R.string.capture_notify_body)
        // The lock screen shows only a generic line; amounts and shops appear once unlocked.
        val public = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.capture_notify_public_title))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "sms_capture"
    }
}
