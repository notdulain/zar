package com.example.zar.acceptance

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.MainActivity
import com.example.zar.features.auth.EmulatorFirebase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Actual app navigation, local Firebase and synthetic tools/task6-seed.mjs data. */
@OptIn(ExperimentalTestApi::class)
class RuntimeAcceptanceEvidenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun tag(s: String) = compose.onAllNodesWithTag(s).onLast()
    private fun capture(name: String) {
        compose.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir = File(inst.targetContext.cacheDir, "task6-runtime").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun recordedHomePlansSpendingAndAppearanceJourney() {
        EmulatorFirebase.requireEmulators()
        compose.waitUntilAtLeastOneExists(hasTestTag("email") or hasTestTag("nav-more"), 20_000)
        runBlocking { FirebaseAuth.getInstance().signInWithEmailAndPassword("benchmark@zar.test", "benchmark-pass-1").await() }
        compose.waitUntilAtLeastOneExists(hasTestTag("received-amount"), 20_000)
        compose.waitUntilExactlyOneExists(hasTestTag("home-goal-name") and hasText("New phone"), 20_000)
        capture("01-home-bars-light")
        tag("received-chart").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .08f, height * .8f)) }
        capture("02-home-tooltip-light")
        compose.onNodeWithContentDescription("Spending donut").performClick()
        capture("03-home-donut-light")
        compose.onNodeWithContentDescription("Received trend").performClick()
        capture("04-home-trend-light")
        tag("home-month-selector").performClick()
        capture("05-home-month-picker")
        pressBack()
        tag("spent-card").performScrollTo().performClick()
        tag("spending-screen").assertIsDisplayed()
        capture("06-spending-light")
        pressBack()
        compose.onNodeWithText("Plans", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("plans-new-plan"), 15_000)
        capture("07-plans-light")
        tag("plans-new-plan").performClick()
        tag("new-plan-name").assertIsDisplayed()
        capture("08-new-plan-light")
        pressBack()
        tag("nav-more").performClick()
        tag("open-settings").performScrollTo().performClick()
        capture("09-settings-light")
        tag("appearance-row").performScrollTo().performClick()
        tag("appearance-option-dark").performClick()
        capture("10-settings-dark")
        pressBack()
        tag("nav-plans").performClick()
        capture("11-plans-dark")
        tag("nav-home").performClick()
        capture("12-home-dark")
        // Restore the test emulator's saved appearance through the production Settings flow.
        tag("nav-more").performClick()
        tag("open-settings").performScrollTo().performClick()
        tag("appearance-row").performScrollTo().performClick()
        tag("appearance-option-light").performClick()
    }
}
