package com.example.zar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.zar.core.navigation.LaunchTargets
import com.example.zar.core.navigation.ZarRoot
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.capture.presentation.SharedTextInbox
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Set when the app was opened from the weekly reminder, and cleared once the catch-up has been opened.
    private var openCatchUp by mutableStateOf(false)

    // Bank text shared from another app. Kept in memory only; the capture screen takes it.
    @Inject lateinit var sharedTextInbox: SharedTextInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openCatchUp = savedInstanceState?.getBoolean(LaunchTargets.EXTRA) ?: intent.isCatchUpLaunch()
        if (savedInstanceState == null) intent.sharedText()?.let(sharedTextInbox::offer)
        setContent {
            ZarTheme {
                ZarRoot(openCatchUp = openCatchUp, onCatchUpOpened = { openCatchUp = false })
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(LaunchTargets.EXTRA, openCatchUp)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.isCatchUpLaunch()) openCatchUp = true
        intent.sharedText()?.let(sharedTextInbox::offer)
    }

    private fun Intent.sharedText(): String? =
        if (action == Intent.ACTION_SEND && type == "text/plain") getStringExtra(Intent.EXTRA_TEXT) else null

    private fun Intent.isCatchUpLaunch(): Boolean = getStringExtra(LaunchTargets.EXTRA) == LaunchTargets.CATCH_UP
}
