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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Set when the app was opened from the weekly reminder, and cleared once the catch-up has been opened.
    private var openCatchUp by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openCatchUp = intent.isCatchUpLaunch()
        setContent {
            ZarTheme {
                ZarRoot(openCatchUp = openCatchUp, onCatchUpOpened = { openCatchUp = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.isCatchUpLaunch()) openCatchUp = true
    }

    private fun Intent.isCatchUpLaunch(): Boolean = getStringExtra(LaunchTargets.EXTRA) == LaunchTargets.CATCH_UP
}
