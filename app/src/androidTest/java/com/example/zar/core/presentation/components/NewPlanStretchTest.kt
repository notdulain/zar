package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.theme.ZarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Add button becoming "New plan" on a controlled clock: nothing changes until Plans has been still for the dwell, page
 * movement restarts the wait, and the stretch itself is a continuous spring that bounces and settles on the pill.
 */
class NewPlanStretchTest {
    @get:Rule val compose = createComposeRule()

    private var destination by mutableStateOf(TopLevelDestination.Home)
    private var pageMoving by mutableStateOf(false)
    private var creates = 0
    private val dwell = ZarMotion.NEW_PLAN_DWELL_MILLIS
    private val circle = 68.dp

    private fun show(policy: MotionPolicy = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    Box(Modifier.fillMaxSize()) {
                        ZarTopLevelChrome(
                            destination = destination,
                            chrome = NavChrome.Full,
                            onNavigate = { destination = it },
                            onAddOption = {},
                            controller = NavCapsuleController(),
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onNewPlan = { creates++ },
                            pageMoving = { pageMoving },
                        )
                    }
                }
            }
        }
        frames(4)
    }

    private fun frames(count: Int = 1) = repeat(count) { compose.mainClock.advanceTimeByFrame() }
    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun width(tag: String = "plans-new-plan"): Dp = bounds(tag).let { it.right - it.left }
    private fun settle() = frames(120)

    private fun arriveAtPlans() {
        destination = TopLevelDestination.Plans
        frames(2)
    }

    @Test
    fun staysThePlusUntilPlansHasBeenStillForTheDwell() {
        show()
        arriveAtPlans()
        assertEquals(circle.value, width().value, 0.5f)
        compose.mainClock.advanceTimeBy(dwell - 200)
        assertEquals("still the plus before the dwell", circle.value, width().value, 0.5f)
        compose.mainClock.advanceTimeBy(400)
        settle()
        assertTrue("became the pill (${width()})", width() > circle + 20.dp)
    }

    @Test
    fun passingThroughPlansNeverReshapesTheButton() {
        show()
        arriveAtPlans()
        compose.mainClock.advanceTimeBy(dwell / 2)
        destination = TopLevelDestination.More
        frames(2)
        compose.mainClock.advanceTimeBy(dwell * 2)
        settle()
        assertEquals(circle.value, width("add-entry").value, 0.5f)
    }

    @Test
    fun pageMovementRestartsTheWait() {
        show()
        arriveAtPlans()
        compose.mainClock.advanceTimeBy(dwell - 200)
        pageMoving = true
        frames(2)
        compose.mainClock.advanceTimeBy(dwell)
        assertEquals("not while the page moves", circle.value, width().value, 0.5f)
        pageMoving = false
        frames(2)
        compose.mainClock.advanceTimeBy(dwell - 200)
        assertEquals("the wait started over", circle.value, width().value, 0.5f)
        compose.mainClock.advanceTimeBy(400)
        settle()
        assertTrue(width() > circle + 20.dp)
    }

    @Test
    fun stretchesContinuouslyWithABounceAndKeepsItsHeightAtRest() {
        show()
        arriveAtPlans()
        compose.mainClock.advanceTimeBy(dwell)
        val trail = mutableListOf<Float>()
        repeat(120) { frames(); trail += width().value }
        val rest = trail.last()
        assertTrue("an intermediate width", trail.any { it > circle.value + 5f && it < rest - 5f })
        assertTrue("overshoots a little (${trail.max() - rest})", trail.max() - rest in 1f..12f)
        trail.zipWithNext().forEach { (a, b) -> assertTrue("jump ${b - a}", kotlin.math.abs(b - a) < 12f) }
        val at = bounds("plans-new-plan")
        assertEquals(circle.value, (at.bottom - at.top).value, 0.5f)
    }

    @Test
    fun leavingPlansWaitsForThePageToLandBeforeShrinking() {
        show()
        arriveAtPlans()
        compose.mainClock.advanceTimeBy(dwell + 200)
        settle()
        val pill = width()
        pageMoving = true
        destination = TopLevelDestination.Activity
        frames(30)
        assertEquals("holds its shape while the page moves", pill.value, width("add-entry").value, 0.5f)
        pageMoving = false
        frames(10)
        assertTrue("then shrinks", width("add-entry") < pill)
        settle()
        assertEquals(circle.value, width("add-entry").value, 0.5f)
    }

    @Test
    fun leavingPlansPullsItBackToThePlusAtOnce() {
        show()
        arriveAtPlans()
        compose.mainClock.advanceTimeBy(dwell + 200)
        settle()
        val pill = width()
        destination = TopLevelDestination.Activity
        frames(2)
        repeat(8) { frames() }
        assertTrue("already shrinking", width("add-entry") < pill)
        settle()
        assertEquals(circle.value, width("add-entry").value, 0.5f)
    }

    @Test
    fun tappingThePlusOnPlansAlreadyCreatesAPlan() {
        show()
        arriveAtPlans()
        compose.onNodeWithTag("plans-new-plan").assertIsDisplayed().performClick()
        frames(2)
        assertEquals(1, creates)
    }

    @Test
    fun reducedStillWaitsThenChangesDirectly() = waitsThenChangesDirectly(MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true))

    @Test
    fun instantStillWaitsThenChangesDirectly() = waitsThenChangesDirectly(MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = false))

    private fun waitsThenChangesDirectly(policy: MotionPolicy) {
        show(policy)
        arriveAtPlans()
        assertEquals(circle.value, width().value, 0.5f)
        compose.mainClock.advanceTimeBy(dwell + 100)
        frames(2)
        val direct = width()
        assertTrue("no travel", direct > circle + 20.dp)
        frames(30)
        assertEquals(direct.value, width().value, 0.5f)
    }
}
