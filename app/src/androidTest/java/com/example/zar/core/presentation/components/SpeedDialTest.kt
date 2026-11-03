package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso.pressBack
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpeedDialTest {
    @get:Rule val compose = createComposeRule()
    private var destination by mutableStateOf(TopLevelDestination.Home)
    private var policy by mutableStateOf(MotionPolicy(false, true))
    private var available by mutableStateOf(true)
    private val selections = mutableListOf<AddEntryOption>()
    private var pageClicks = 0

    private fun show(chrome: NavChrome = NavChrome.AddOnly) {
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    Box(Modifier.fillMaxSize().testTag("window")) {
                        var menuOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
                        Box(Modifier.then(if (menuOpen) Modifier.clearAndSetSemantics { } else Modifier)) {
                            TextButton(onClick = { pageClicks++ }, modifier = Modifier.testTag("page-control")) { Text("Page control") }
                        }
                        ZarTopLevelChrome(destination, chrome, { destination = it }, { selections += it },
                            androidx.compose.runtime.remember { NavCapsuleController() },
                            enabled = available, onMenuOpenChange = { menuOpen = it })
                    }
                }
            }
        }
    }

    @Test
    fun homeOpensOnlyTwoActionsAndDismissesLocallyWithAnInertClosingFrame() {
        show()
        val anchor = compose.onNodeWithTag("add-entry").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("add-quick-note").assertDoesNotExist()
        compose.onNodeWithTag("add-entry").performClick()
        compose.onNodeWithTag("add-quick-note").assertIsDisplayed()
        compose.onNodeWithTag("add-manual").assertIsDisplayed()
        compose.onNodeWithTag("nav-home").assertDoesNotExist()
        compose.onNodeWithTag("page-control").assertDoesNotExist()
        assertEquals(anchor, compose.onNodeWithTag("add-entry").getUnclippedBoundsInRoot())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("add-entry").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("add-manual").assertDoesNotExist()
        compose.onNodeWithTag("page-control").performClick()
        assertEquals(1, pageClicks)
        assertEquals(emptyList<AddEntryOption>(), selections)
    }

    @Test
    fun fullChromeKeepsTabsStationaryAndDoubleTapSelectsOnce() {
        show(NavChrome.Full)
        val tabs = compose.onNodeWithTag("nav-activity").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("add-entry").performClick()
        assertEquals(tabs, compose.onNodeWithTag("nav-activity").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("nav-activity").performClick()
        compose.onNodeWithTag("add-manual").assertDoesNotExist()
        assertEquals(TopLevelDestination.Activity, destination)
        compose.onNodeWithTag("add-entry").performClick()
        compose.onNodeWithTag("add-manual").performTouchInput { doubleClick() }
        assertEquals(listOf(AddEntryOption.Manual), selections)
        compose.onNodeWithTag("add-entry").performClick()
        compose.onNodeWithTag("add-quick-note").performClick()
        assertEquals(listOf(AddEntryOption.Manual, AddEntryOption.QuickNote), selections)
    }

    @Test
    fun backOutsideAndCoveringRouteAllResetTheDial() {
        show()
        compose.onNodeWithTag("add-entry").performClick()
        pressBack()
        compose.onNodeWithTag("add-manual").assertDoesNotExist()
        compose.onNodeWithTag("add-entry").performClick()
        compose.onNodeWithTag("window").performTouchInput { down(Offset(12f, 12f)); moveTo(Offset(12f, 200f)); up() }
        compose.onNodeWithTag("add-manual").assertDoesNotExist()
        assertEquals(0, pageClicks)
        compose.onNodeWithTag("add-entry").performClick()
        compose.runOnIdle { available = false }
        compose.onNodeWithTag("add-manual").assertDoesNotExist()
        compose.runOnIdle { available = true }
        compose.onNodeWithTag("add-manual").assertDoesNotExist()
    }

    @Test
    fun reducedAndInstantUseRestPositionsAndRapidTapsRemainResponsive() {
        policy = MotionPolicy(true, true)
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("add-entry").performClick()
        compose.mainClock.advanceTimeBy(128)
        val anchor = compose.onNodeWithTag("add-entry").getUnclippedBoundsInRoot()
        val quick = compose.onNodeWithTag("add-quick-note").getUnclippedBoundsInRoot()
        val manual = compose.onNodeWithTag("add-manual").getUnclippedBoundsInRoot()
        assertEquals(136f, (anchor.top.value + anchor.bottom.value - quick.top.value - quick.bottom.value) / 2, .5f)
        assertEquals(72f, (anchor.top.value + anchor.bottom.value - manual.top.value - manual.bottom.value) / 2, .5f)
        compose.onNodeWithTag("add-entry").performClick()
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { policy = MotionPolicy(false, false) }
        compose.onNodeWithTag("add-entry").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("add-manual").assertIsDisplayed()
        compose.onNodeWithTag("add-entry").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("add-manual").assertDoesNotExist()
        compose.runOnIdle { policy = MotionPolicy(false, true) }
        repeat(9) { compose.onNodeWithTag("add-entry").performClick(); compose.mainClock.advanceTimeBy(48) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("add-manual").assertIsDisplayed()
    }
    @OptIn(androidx.compose.runtime.ExperimentalComposeApi::class)
    @Test
    fun springFramesDoNotRecomposeTheChrome() {
        show(NavChrome.Full)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("add-entry").performClick()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        var before = 0L
        compose.runOnIdle { before = androidx.compose.runtime.Recomposer.runningRecomposers.value.sumOf { it.changeCount } }
        repeat(12) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.runOnIdle {
            val changes = androidx.compose.runtime.Recomposer.runningRecomposers.value.sumOf { it.changeCount } - before
            android.util.Log.i("ZarSpeedDialPerf", "Recomposition batches during 12 spring frames: $changes")
            org.junit.Assert.assertTrue("Spring frames must update layers, not recompose chrome ($changes batches)", changes <= 2)
        }
        compose.mainClock.advanceTimeBy(600)
    }

    @Test
    fun homeFixtureRendersBothThemesAndChromeModes() {
        var dark by mutableStateOf(false)
        var session by mutableStateOf(NavSession())
        val month = java.time.YearMonth.of(2026, 11)
        val zero = com.example.zar.core.domain.calculation.ReportingTotal.zero()
        val overview = com.example.zar.features.home.domain.model.HomeOverview(
            month = month,
            summary = com.example.zar.core.domain.calculation.PeriodSummary(zero,
                com.example.zar.core.domain.calculation.SpendingBreakdown(zero, zero, zero), 0),
            recent = emptyList(), hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { zero },
        )
        compose.setContent {
            ZarTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalNavSession provides session) {
                    com.example.zar.features.home.presentation.screen.HomeScreenContent(
                        state = com.example.zar.features.home.presentation.viewmodel.HomeUiState(month, false,
                            com.example.zar.features.home.presentation.viewmodel.HomeContent.Ready(overview)),
                        onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                    )
                }
            }
        }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; session = NavSession() }
            for (mode in listOf("home", "full")) {
                if (mode == "full") compose.runOnIdle { session.onTopLevelArrived(TopLevelDestination.Activity) }
                compose.onNodeWithTag("add-entry").performClick()
                saveEvidence("$theme-$mode-open")
                compose.onNodeWithTag("add-quick-note").assertIsDisplayed()
                compose.onNodeWithTag("add-manual").assertIsDisplayed()
                compose.onNodeWithTag("add-entry").performClick()
                compose.onNodeWithTag("add-manual").assertDoesNotExist()
                saveEvidence("$theme-$mode-closed")
            }
        }
    }

    private fun saveEvidence(name: String) {
        compose.waitForIdle()
        // Android's render-thread ripple uses real time, independently of the controlled Compose clock.
        android.os.SystemClock.sleep(600)
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "speed-dial").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(directory, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

}
