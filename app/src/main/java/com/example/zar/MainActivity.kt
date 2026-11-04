package com.example.zar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.core.navigation.LaunchTargets
import com.example.zar.core.navigation.ZarRoot
import com.example.zar.core.presentation.motion.ProvideZarMotion
import com.example.zar.core.presentation.theme.ProvideBaselineProportions
import com.example.zar.core.presentation.theme.ZarAppearance
import com.example.zar.core.presentation.theme.applyZarAppearance
import com.example.zar.core.presentation.theme.isSystemNightMode
import com.example.zar.features.capture.presentation.SharedTextInbox
import com.example.zar.features.settings.domain.usecase.ObserveDisplayPreferencesUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Set when the app was opened from the weekly reminder, and cleared once the catch-up has been opened.
    private var openCatchUp by mutableStateOf(false)

    // Set when a capture notification was tapped, and cleared once that draft's review has been opened.
    private var openReviewDraft by mutableStateOf<String?>(null)

    // Bank text shared from another app. Kept in memory only; the capture screen takes it.
    @Inject lateinit var sharedTextInbox: SharedTextInbox

    // How this phone shows the app. Read synchronously before the first frame so the saved appearance never flashes.
    @Inject lateinit var displayPreferences: ObserveDisplayPreferencesUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyZarAppearance(dark = displayPreferences.current().appearance.resolveDark(resources.configuration.isSystemNightMode()))
        openCatchUp = savedInstanceState?.getBoolean(LaunchTargets.EXTRA) ?: intent.isCatchUpLaunch()
        openReviewDraft = if (savedInstanceState != null) savedInstanceState.getString(LaunchTargets.EXTRA_DRAFT_ID) else intent.reviewDraft()
        takeSharedText(intent)
        setContent {
            val display by displayPreferences().collectAsStateWithLifecycle(initialValue = displayPreferences.current())
            ZarAppearance(display.appearance) {
                // Keeps the accepted default composition when Android's display/font size is below default. A no-op at
                // or above default; see ZarProportionPolicy for why it only ever raises the layout density.
                ProvideBaselineProportions {
                    ProvideZarMotion(display.reduceMotion) {
                        ZarRoot(
                            openCatchUp = openCatchUp,
                            onCatchUpOpened = { openCatchUp = false },
                            openReviewDraft = openReviewDraft,
                            onReviewOpened = { openReviewDraft = null },
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(LaunchTargets.EXTRA, openCatchUp)
        outState.putString(LaunchTargets.EXTRA_DRAFT_ID, openReviewDraft)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.isCatchUpLaunch()) openCatchUp = true
        intent.reviewDraft()?.let { openReviewDraft = it }
        takeSharedText(intent)
    }

    private fun takeSharedText(launch: Intent) {
        if (launch.action != Intent.ACTION_SEND || launch.type != "text/plain") return
        val text = launch.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        launch.removeExtra(Intent.EXTRA_TEXT)
        launch.clipData = null
        text?.let(sharedTextInbox::offer)
    }

    private fun Intent.isCatchUpLaunch(): Boolean = getStringExtra(LaunchTargets.EXTRA) == LaunchTargets.CATCH_UP

    private fun Intent.reviewDraft(): String? =
        getStringExtra(LaunchTargets.EXTRA_DRAFT_ID)?.takeIf { getStringExtra(LaunchTargets.EXTRA) == LaunchTargets.CAPTURE_REVIEW }
}
