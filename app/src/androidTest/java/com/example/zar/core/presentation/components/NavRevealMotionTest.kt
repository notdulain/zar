package com.example.zar.core.presentation.components

import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.theme.ZarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `persist-home-navigation` with `add-fluid-motion` 4.2 on a controlled clock: a fresh Home shows only +; the first
 * departure reveals the pill with a quiet rise (at most 8dp, about 160ms, no spring) and places the capsule on the
 * actual tab without a flight; leaving and returning does not replay the reveal.
 */
class NavRevealMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val controller = NavCapsuleController()
    private var chrome by mutableStateOf(NavChrome.AddOnly)
    private var destination by mutableStateOf(TopLevelDestination.Home)
    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)

    private fun show(policy: MotionPolicy) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    ZarTopLevelChrome(
                        destination = destination,
                        chrome = chrome,
                        onNavigate = { destination = it; chrome = NavChrome.Full },
                        onAddOption = {},
                        controller = controller,
                    )
                }
            }
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun frame(count: Int = 1) = repeat(count) { compose.mainClock.advanceTimeByFrame() }
    private fun navTop() = compose.onNodeWithTag("nav-activity").getUnclippedBoundsInRoot().top.value

    @Test
    fun aFreshHomeShowsOnlyAddAndNothingMoves() {
        show(full)

        compose.onNodeWithTag("add-entry").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTagCount("nav-home"))
        assertFalse(controller.revealed)
    }

    @Test
    fun theFirstDepartureRevealsThePillWithAQuietRiseAndNoCapsuleFlight() {
        show(full)

        destination = TopLevelDestination.Activity
        chrome = NavChrome.Full
        frame()
        val tops = ArrayList<Float>()
        val capsuleLeft = ArrayList<Float>()
        repeat(20) {
            frame()
            if (compose.onAllNodesWithTagCount("nav-activity") > 0) {
                tops += navTop()
                controller.capsuleBounds()?.let { capsuleLeft += it.left }
            }
        }
        Log.i("ZarNavReveal", "nav top dp per frame: $tops; capsule left px per frame: $capsuleLeft")

        assertTrue("the pill appears", tops.isNotEmpty())
        val rest = tops.last()
        assertTrue("rises no more than ${ZarMotion.NavRevealRise}: ${tops.first() - rest}", tops.first() - rest <= ZarMotion.NavRevealRise.value + 0.6f)
        assertTrue("it rises into place and never sinks or overshoots: $tops", tops.zipWithNext().all { (a, b) -> b <= a + 0.01f } && tops.all { it >= rest - 0.01f })
        assertTrue("the capsule is on Activity from its first frame, it never flies in: $capsuleLeft", capsuleLeft.isNotEmpty() && capsuleLeft.all { kotlin.math.abs(it - capsuleLeft.first()) < 0.5f })
        compose.onNodeWithTag("nav-activity").assertIsDisplayed()
    }

    @Test
    fun reducedMotionRevealsInPlaceWithoutARise() {
        show(reduced)

        destination = TopLevelDestination.Activity
        chrome = NavChrome.Full
        frame()
        val tops = ArrayList<Float>()
        repeat(12) {
            frame()
            if (compose.onAllNodesWithTagCount("nav-activity") > 0) tops += navTop()
        }

        assertTrue("shown", tops.isNotEmpty())
        assertTrue("no rise under reduced motion: $tops", tops.all { kotlin.math.abs(it - tops.last()) < 0.5f })
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
    private fun onAllNodesWithTagCountOf(tag: String) = compose.onAllNodesWithTagCount(tag)
}
