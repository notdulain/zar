package com.example.zar.features.reminders.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.zar.R
import com.example.zar.features.transactions.presentation.income.screen.ZarMono

/** Whether Zar may show notifications, kept current when the person returns from settings. */
class NotificationAccess(val enabled: Boolean, val request: () -> Unit)

private fun Context.notificationsEnabled() = NotificationManagerCompat.from(this).areNotificationsEnabled()

/**
 * Asks for notification permission only when a reminder is switched on. A refusal is respected:
 * the reminder setting is kept, but nothing is shown and the screen says so.
 */
@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(context.notificationsEnabled()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { enabled = context.notificationsEnabled() }
    LifecycleResumeEffect(Unit) {
        enabled = context.notificationsEnabled()
        onPauseOrDispose { }
    }
    return NotificationAccess(enabled) {
        val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsking) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun NotificationsOffNote() {
    Text(stringResource(R.string.reminders_notifications_off), style = MaterialTheme.typography.bodySmall, fontFamily = ZarMono, color = MaterialTheme.colorScheme.error)
}
