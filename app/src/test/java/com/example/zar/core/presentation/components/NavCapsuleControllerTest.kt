package com.example.zar.core.presentation.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.geometry.Rect
import com.example.zar.core.presentation.motion.FrameDriver
import com.example.zar.core.presentation.motion.ZarSprings
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The retained navbar mover: one capsule that places itself on the real selection on first reveal, then springs its
 * measured x/width toward the selected slot, retargets from where it is, bounces a little at the ends and never leaves
 * the pill. A frame driver stands in for the display, so every flight is deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavCapsuleControllerTest {

    private fun slots(vararg lefts: Float, width: Float = 72f, top: Float = 4f, height: Float = 59f) =
        lefts.map { Rect(it, top, it + width, top + height) }

    private val ltr = slots(6f, 80f, 154f, 228f)

    /** The pill interior: 6 px of padding either side of the slots, the room a 6 px overshoot can use. */
    private val interior = Rect(1f, 1f, 307f, 66f)
    private val overshoot = 6f

    private val navSpring = spring<Rect>(ZarSprings.NavSelection.dampingRatio, ZarSprings.NavSelection.stiffness, Rect.VisibilityThreshold)

    private fun controller(measured: List<Rect> = ltr) = NavCapsuleController().apply {
        maxOvershootPx = overshoot
        onSlotsMeasured(measured, interior)
    }

    /** Starts a flight to the controller's target in the background of [scope] and returns the job. */
    private fun TestScope.fly(driver: FrameDriver, controller: NavCapsuleController): Job =
        launch(driver.clock) { controller.moveTo(controller.targetRect!!, navSpring) }

    @Test
    fun beforeMeasurementThereIsNoCapsule() {
        val controller = NavCapsuleController()

        assertNull(controller.capsuleBounds())
        assertFalse(controller.revealed)
        assertEquals(0, controller.slotCount)
        assertNull(controller.targetRect)
    }

    @Test
    fun firstRevealPlacesTheCapsuleOnTheActualSelectionWithoutFlying() = runTest {
        val controller = controller()
        controller.select(2)

        controller.moveTo(controller.targetRect!!, navSpring)

        assertTrue(controller.revealed)
        assertEquals(2, controller.targetIndex)
        assertEquals(ltr[2], controller.capsuleBounds())
        assertFalse(controller.isMoving)
    }

    @Test
    fun theCapsuleSpringsFromSlotToSlotAndComesToRest() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller()
        controller.snapTo(ltr[0])
        controller.select(1)

        val job = fly(driver, controller)
        runCurrent()
        driver.step()
        driver.stepFor(60.0)
        val mid = controller.capsuleBounds()!!
        assertTrue("mid-flight left ${mid.left}", mid.left > ltr[0].left && mid.left < ltr[2].left)
        assertTrue(controller.isMoving)

        repeat(120) { if (job.isActive) driver.step() }

        assertFalse(job.isActive)
        val settled = controller.capsuleBounds()!!
        assertEquals(ltr[1].left, settled.left, 0.6f)
        assertEquals(ltr[1].right, settled.right, 0.6f)
    }

    @Test
    fun aNewSelectionMidFlightRetargetsFromTheOnScreenPositionWithoutTeleporting() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller()
        controller.snapTo(ltr[0])
        controller.select(3)
        val first = fly(driver, controller)
        runCurrent()
        driver.step()
        driver.stepFor(80.0)
        val before = controller.capsuleBounds()!!
        val travelVelocity = controller.velocityLeftEdge
        assertTrue("moving toward More", travelVelocity > 0f)

        // The user taps Home. The old flight is cancelled, never queued, and the new one starts here.
        first.cancel()
        controller.select(0)
        val second = fly(driver, controller)
        runCurrent()
        driver.step()
        val after = controller.capsuleBounds()!!

        assertTrue("jumped ${abs(after.left - before.left)}px", abs(after.left - before.left) < 14f)
        assertTrue("momentum carries on, it does not stop dead", after.left >= before.left - 1f)
        assertTrue("the first frame after a retarget moves (no one-frame hitch): ${after.left - before.left}", abs(after.left - before.left) > 0.5f)
        repeat(240) { if (second.isActive) driver.step() }
        assertFalse(second.isActive)
        assertEquals(ltr[0].left, controller.capsuleBounds()!!.left, 0.6f)
    }

    @Test
    fun theLastTabBouncesPastItsSlotButNeverPastTheOvershootBoundOrThePill() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_120)
        val controller = controller()
        controller.snapTo(ltr[0])
        controller.select(3)

        val job = fly(driver, controller)
        runCurrent()
        var peakRight = 0f
        var narrowest = Float.MAX_VALUE
        repeat(400) {
            driver.step()
            val bounds = controller.capsuleBounds()!!
            peakRight = maxOf(peakRight, bounds.right)
            narrowest = minOf(narrowest, bounds.width)
        }
        assertTrue("the bounce squashed the capsule to $narrowest", narrowest >= ltr[3].width - 0.01f)

        val end = ltr[3].right
        assertTrue("no visible overshoot at the end: peak $peakRight vs $end", peakRight > end + 1f)
        assertTrue("overshoot $peakRight exceeded the ${overshoot}px bound", peakRight <= end + overshoot + 0.001f)
        assertTrue("left the pill: $peakRight", peakRight <= interior.right)
        assertFalse(job.isActive)
    }

    @Test
    fun theFirstTabBouncesPastItsSlotWithinTheSameBound() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller()
        controller.snapTo(ltr[3])
        controller.select(0)

        fly(driver, controller)
        runCurrent()
        var peakLeft = Float.MAX_VALUE
        repeat(240) {
            driver.step()
            peakLeft = minOf(peakLeft, controller.capsuleBounds()!!.left)
        }

        assertTrue("no visible overshoot at the start: $peakLeft", peakLeft < ltr[0].left - 1f)
        assertTrue("past the overshoot bound: $peakLeft", peakLeft >= ltr[0].left - overshoot - 0.001f)
        assertTrue("left the pill: $peakLeft", peakLeft >= interior.left)
    }

    @Test
    fun aShortHopBouncesOnlyAFewPixels() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller()
        controller.snapTo(ltr[0])
        controller.select(1)

        fly(driver, controller)
        runCurrent()
        var peak = 0f
        repeat(120) {
            driver.step()
            peak = maxOf(peak, controller.capsuleBounds()!!.right - ltr[1].right)
        }

        assertTrue("short hop overshoot $peak", peak in 0f..4f)
    }

    @Test
    fun theCapsuleUsesPhysicalSlotPositionsSoRtlMirrorsForFree() = runTest {
        val rtl = slots(228f, 154f, 80f, 6f)
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller(rtl)
        controller.select(0)
        controller.moveTo(controller.targetRect!!, navSpring)

        // Index 0 is still the first logical tab, but it is physically on the right in RTL.
        assertEquals(rtl[0], controller.capsuleBounds())
        controller.select(3)
        fly(driver, controller)
        runCurrent()
        var peakLeft = Float.MAX_VALUE
        repeat(300) {
            driver.step()
            peakLeft = minOf(peakLeft, controller.capsuleBounds()!!.left)
        }
        assertTrue("RTL overshoot toward the left edge: $peakLeft", peakLeft < rtl[3].left - 1f && peakLeft >= rtl[3].left - overshoot - 0.001f)
        assertEquals(rtl[3].left, controller.capsuleBounds()!!.left, 0.6f)
    }

    @Test
    fun theCapsuleWidthSpringsWithItWhenTheSelectedSlotWidens() = runTest {
        // Selecting a tab widens its slot (66 -> 75 in the layout) and narrows the old one, in the same frame.
        val before = slots(6f, 80f, 154f, 228f)
        val after = listOf(
            Rect(6f, 4f, 66f, 63f),
            Rect(68f, 4f, 143f, 63f), // newly selected: wider
            Rect(145f, 4f, 211f, 63f),
            Rect(213f, 4f, 279f, 63f),
        )
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller(before)
        controller.snapTo(before[0])
        controller.select(1)
        controller.onSlotsMeasured(after, interior)

        fly(driver, controller)
        runCurrent()
        driver.step()
        driver.step()
        val first = controller.capsuleBounds()!!

        // No pop on the frame the layout changes: the capsule still looks like the old Home capsule.
        assertEquals(before[0].width, first.width, 8f)
        assertEquals(before[0].left, first.left, 8f)
        repeat(240) { driver.step() }
        assertEquals(after[1].width, controller.capsuleBounds()!!.width, 0.8f)
    }

    @Test
    fun selectionIsClampedToTheMeasuredSlots() {
        val controller = controller()

        controller.select(99)

        assertEquals(3, controller.targetIndex)
    }

    @Test
    fun snapToResolvesImmediatelyForReducedMotion() = runTest {
        val controller = controller()
        controller.select(3)

        controller.snapTo(controller.targetRect!!)

        assertEquals(ltr[3], controller.capsuleBounds())
        assertFalse(controller.isMoving)
    }

    @Test
    fun remeasurementKeepsTheSelectionAndTheCapsuleFollowsItsNewSlot() = runTest {
        val controller = controller()
        controller.select(3)
        controller.snapTo(controller.targetRect!!)

        // A font-size or rotation change moves the slots; the same destination stays selected.
        val moved = ltr.map { Rect(it.left + 10f, it.top, it.right + 10f, it.bottom) }
        controller.onSlotsMeasured(moved, interior)

        assertEquals(3, controller.targetIndex)
        assertEquals(moved[3], controller.targetRect)
    }

    @Test
    fun theCapsuleNeverDrawsOutsideTheMeasuredBounds() = runTest {
        val controller = controller()
        controller.snapTo(Rect(-50f, 4f, 20f, 63f))

        val bounds = controller.capsuleBounds()!!

        assertTrue(bounds.left >= interior.left)
        assertTrue(bounds.left >= ltr[0].left - overshoot)
    }
}
