package com.example.zar.core.presentation.components

import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The navbar capsule on a controlled Compose clock: nothing advances until the test says so, so a flight, an
 * interruption and the reduced/instant paths are deterministic. Frames are 16 ms here; this is not a refresh-rate test.
 * The per-frame travel is logged under `ZarNavTravel` for the evidence record.
 */
class ZarBottomNavMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val controller = NavCapsuleController()
    private var selected by mutableStateOf(TopLevelDestination.Home)

    private fun show(policy: MotionPolicy) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    ZarBottomNav(selected, onNavigate = { selected = it }, controller = controller)
                }
            }
        }
        // Compose the first frame, measure the slots and let the capsule place itself.
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun frame(count: Int = 1) = repeat(count) { compose.mainClock.advanceTimeByFrame() }

    private fun left() = controller.capsuleBounds()!!.left
    private fun right() = controller.capsuleBounds()!!.right
    private fun tap(tag: String) = compose.onNodeWithTag(tag).performClick()

    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)
    private val instant = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = false)

    @Test
    fun firstRevealPlacesTheCapsuleOnHomeWithoutAFlight() {
        show(full)

        val placed = left()
        frame(6)

        assertEquals(placed, left(), 0.01f)
        assertFalse(controller.isMoving)
        compose.onNodeWithTag("nav-home").assertIsSelected()
    }

    @Test
    fun theCapsuleTravelsWithASpringAndTheLabelsStayReadable() {
        show(full)
        val startLeft = left()
        val log = StringBuilder("Home->More left px:")

        tap("nav-more")
        frame() // the click is composed on the next frame
        compose.onNodeWithTag("nav-more").assertIsSelected()
        var peak = right()
        val bounds = ArrayList<Pair<Float, Float>>()
        repeat(40) { index ->
            frame()
            log.append(" %.1f".format(left()))
            peak = maxOf(peak, right())
            if (index == 5) assertTrue("capsule still at Home on frame 6", left() > startLeft + 1f)
            listOf("nav-home", "nav-activity", "nav-plans", "nav-more").forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
            val more = compose.onNodeWithTag("nav-more").getUnclippedBoundsInRoot()
            bounds += more.left.value to more.width.value
        }
        Log.i("ZarNavTravel", log.toString())

        val rest = controller.targetRect!!
        assertFalse(controller.isMoving)
        assertEquals(rest.left, left(), 0.7f)
        assertTrue("More (last tab) should bounce a little past its slot: peak $peak vs ${rest.right}", peak > rest.right + 0.5f)
        // Touch bounds are stable once the selection has been laid out: they do not follow the moving capsule.
        assertEquals(1, bounds.drop(1).distinct().size)
    }

    @Test
    fun aRapidSecondTapRetargetsFromTheCurrentPositionAndTheLastTapWins() {
        show(full)
        tap("nav-more")
        val steps = ArrayList<Float>()
        var last = left()
        repeat(6) {
            frame()
            steps += left() - last
            last = left()
        }
        Log.i("ZarNavTravel", "retarget approach per-frame dx px: $steps")
        assertTrue("travelling toward More", controller.velocityLeftEdge > 0f)

        // performClick itself lets one frame through, so measure steps from the position right after the tap.
        tap("nav-activity")
        var l = left()
        val after = ArrayList<Float>()
        repeat(6) { frame(); after += left() - l; l = left() }
        compose.onNodeWithTag("nav-activity").assertIsSelected()
        val afterStep = after.first { it != 0f }
        Log.i("ZarNavTravel", "retarget after second tap per-frame dx px: $after")

        // No teleport: the first frame after the tap continues the momentum it had, it does not jump to the new tab.
        assertTrue("first step after the tap $afterStep vs approach ${steps.maxOrNull()}", kotlin.math.abs(afterStep) <= steps.maxOf { kotlin.math.abs(it) } * 1.5f + 2f)
        // The retarget never holds the capsule still for a frame while it is travelling.
        assertTrue("a frame stalled after the retarget: $after", after.take(4).none { it == 0f })
        frame(60)
        assertEquals(controller.targetRect!!.left, left(), 0.7f)
        assertEquals(1, controller.targetIndex)
    }

    @Test
    fun tenRapidTapsEndOnTheLastOneWithoutQueueingAnyBounce() {
        show(full)
        val order = listOf("nav-activity", "nav-plans", "nav-more", "nav-home", "nav-more", "nav-activity", "nav-plans", "nav-home", "nav-activity", "nav-plans")
        order.forEach {
            tap(it)
            frame(2)
        }
        frame()
        compose.onNodeWithTag("nav-plans").assertIsSelected()
        frame(80)

        assertEquals(2, controller.targetIndex)
        assertEquals(controller.targetRect!!.left, left(), 0.7f)
        assertFalse(controller.isMoving)
    }

    @Test
    fun reducedMotionMovesTheCapsuleToTheTargetAtOnceWithNoBounce() {
        show(reduced)

        tap("nav-more")
        frame(3)

        compose.onNodeWithTag("nav-more").assertIsSelected()
        assertFalse(controller.isMoving)
        assertEquals(controller.targetRect!!.left, left(), 0.7f)
        assertEquals(controller.targetRect!!.right, right(), 0.7f)
    }

    @Test
    fun disabledSystemAnimationsAlsoLandOnTheFinalState() {
        show(instant)

        tap("nav-plans")
        frame(3)

        compose.onNodeWithTag("nav-plans").assertIsSelected()
        assertFalse(controller.isMoving)
        assertEquals(controller.targetRect!!.left, left(), 0.7f)
    }

    @Test
    fun theTabBarKeepsItsRestingSizeWhateverTheCapsuleIsDoing() {
        show(full)
        val rest = compose.onNodeWithTag("nav-home").getUnclippedBoundsInRoot().height
        tap("nav-more")
        frame(3)
        assertEquals(59f, rest.value, 0.2f)

        // Home is no longer selected: its resting height is the unselected 56dp, the selected one 59dp.
        assertEquals(56f, compose.onNodeWithTag("nav-home").getUnclippedBoundsInRoot().height.value, 0.2f)
        assertEquals(59f, compose.onNodeWithTag("nav-more").getUnclippedBoundsInRoot().height.value, 0.2f)
    }
}
