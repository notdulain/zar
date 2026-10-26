package com.example.zar.core.presentation.motion

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Provides [LocalZarMotion] for everything below it, in both navigation graphs: put it directly under
 * [com.example.zar.core.presentation.theme.ZarAppearance] around `ZarRoot`. It follows the saved Reduce motion
 * switch and Android's animation setting live, and changes neither navigation, scroll nor any draft.
 */
@Composable
fun ProvideZarMotion(reduceMotion: Boolean, content: @Composable () -> Unit) {
    val animatorScale = rememberAnimatorDurationScale()
    val policy = remember(reduceMotion, animatorScale) { MotionPolicy.from(reduceMotion, animatorScale) }
    CompositionLocalProvider(LocalZarMotion provides policy, content = content)
}

@Composable
private fun rememberAnimatorDurationScale(): Float {
    val context = LocalContext.current
    var scale by remember(context) { mutableFloatStateOf(context.readAnimatorDurationScale()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = context.readAnimatorDurationScale()
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        // The setting may have changed between the first read and the observer registering.
        scale = context.readAnimatorDurationScale()
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return scale
}

private fun Context.readAnimatorDurationScale(): Float =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
