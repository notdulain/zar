package com.example.zar.core.presentation.components

import androidx.compose.animation.core.spring
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

    private fun slots(vararg lefts: Float, width: Float = 72f, top: Float = 5f, height: Float = 56f) =
        lefts.map { Rect(it, top, it + width, top + height) }

    private val ltr = slots(6f, 80f, 154f, 228f)

    /** The pill interior: 6 px of padding either side of the slots, the room a 6 px overshoot can use. */
    private val interior = Rect(1f, 1f, 307f, 66f)
    private val overshoot = 6f

    private val navSpring = spring<Float>(ZarSprings.NavSelection.dampingRatio, ZarSprings.NavSelection.stiffness, 0.0005f)

    private fun controller(measured: List<Rect> = ltr) = NavCapsuleController().apply {
        maxOvershootPx = overshoot
        capsuleHeightPx = 59f
        onSlotsMeasured(measured, interior)
    }

    /** Starts a flight to the controller's target in the background of [scope] and returns the job. */
    private fun TestScope.fly(driver: FrameDriver, controller: NavCapsuleController): Job =
        launch(driver.clock) { controller.moveTo(controller.targetIndex, navSpring) }

    private fun NavCapsuleController.left() = capsuleBounds()!!.left
    private fun NavCapsuleController.right() = capsuleBounds()!!.right

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

        controller.moveTo(controller.targetIndex, navSpring)

        assertTrue(controller.revealed)
        assertEquals(2, controller.targetIndex)
        assertEquals(ltr[2].left, controller.left(), 0.001f)
        assertEquals(ltr[2].right, controller.right(), 0.001f)
        assertFalse(controller.isMoving)
    }

    @Test
    fun theCapsuleIsAsTallAsItsOwnHeightAndCentredOnTheSlots() = runTest {
        val controller = controller()
        controller.snapTo(0f)

        val bounds = controller.capsuleBounds()!!

        assertEquals(59f, bounds.height, 0.001f)
        assertEquals(ltr[0].center.y, bounds.center.y, 0.001f)
    }

    @Test
    fun dragToFollowsThePagerOneForOneBetweenTwoSlots() = runTest {
        val controller = controller()
        controller.snapTo(0f)

        listOf(0f, 0.25f, 0.5f, 0.9f, 1f, 1.5f, 2.99f).forEach { position ->
            controller.dragTo(position)
            val lower = position.toInt().coerceAtMost(2)
            val fraction = position - lower
            val expected = ltr[lower].left + (ltr[lower + 1].left - ltr[lower].left) * fraction
            assertEquals("capsule left at slot position $position", expected, controller.left(), 0.001f)
            assertEquals("the capsule never changes width while it is dragged", ltr[0].width, controller.capsuleBounds()!!.width, 0.001f)
        }
    }

    @Test
    fun theCapsuleSpringsFromSlotToSlotAndComesToRest() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller()
        controller.snapTo(0f)
        controller.select(1)

        val job = fly(driver, controller)
        runCurrent()
        driver.step()
        driver.stepFor(60.0)
        val mid = controller.left()
        assertTrue("mid-flight left $mid", mid > ltr[0].left && mid < ltr[2].left)
        assertTrue(controller.isMoving)

        repeat(120) { if (job.isActive) driver.step() }

        assertFalse(job.isActive)
        assertEquals(ltr[1].left, controller.left(), 0.6f)
        assertEquals(ltr[1].right, controller.right(), 0.6f)
    }

    @Test
    fun noFrameCallbackIsLeftOutstandingOnceTheCapsuleSettlesOrIsCancelled() = runTest {
        // An idle or backgrounded app must not keep asking for frames (and so for a display cadence) on the capsule's behalf.
        val driver = FrameDriver(this, FrameDriver.HZ_120)
        val controller = controller()
        controller.snapTo(0f)
        controller.select(2)
        val settled = fly(driver, controller)
        runCurrent()
        assertTrue("a flight in progress waits for frames", driver.clock.hasAwaiters)

        repeat(400) { if (settled.isActive) driver.step() }
        assertFalse(settled.isActive)
        assertFalse("settled: nothing is waiting for another frame", driver.clock.hasAwaiters)

        controller.select(0)
        val cancelled = fly(driver, controller)
        runCurrent()
        driver.step()
        driver.step()
        cancelled.cancel()
        runCurrent()
        assertFalse("cancelled: nothing is waiting for another frame", driver.clock.hasAwaiters)
        assertFalse(controller.isMoving)
    }

    @Test
    fun aNewSelectionMidFlightRetargetsFromTheOnScreenPositionWithoutTeleporting() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller()
        controller.snapTo(0f)
        controller.select(3)
        val first = fly(driver, controller)
        runCurrent()
        driver.step()
        driver.stepFor(80.0)
        val before = controller.left()
        assertTrue("moving toward More", controller.velocitySlots > 0f)

        // The user taps Home. The old flight is cancelled, never queued, and the new one starts here.
        first.cancel()
        controller.select(0)
        val second = fly(driver, controller)
        runCurrent()
        driver.step()
        val after = controller.left()

        assertTrue("jumped ${abs(after - before)}px", abs(after - before) < 14f)
        assertTrue("momentum carries on, it does not stop dead", after >= before - 1f)
        assertTrue("the first frame after a retarget moves (no one-frame hitch): ${after - before}", abs(after - before) > 0.5f)
        repeat(240) { if (second.isActive) driver.step() }
        assertFalse(second.isActive)
        assertEquals(ltr[0].left, controller.left(), 0.6f)
    }

    @Test
    fun theCapsuleNeverGoesPastMoreAndLandsOnItsSlot() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_120)
        val controller = controller()
        controller.snapTo(0f)
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
        assertTrue("the end stop squashed the capsule to $narrowest", narrowest >= ltr[3].width - 0.01f)
        assertTrue("overshot More: peak $peakRight vs ${ltr[3].right}", peakRight <= ltr[3].right + 0.001f)
        assertEquals(ltr[3].right, controller.right(), 0.6f)
        assertFalse(job.isActive)
    }

    @Test
    fun theCapsuleNeverGoesPastHome() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller()
        controller.snapTo(3f)
        controller.select(0)

        fly(driver, controller)
        runCurrent()
        var peakLeft = Float.MAX_VALUE
        repeat(240) {
            driver.step()
            peakLeft = minOf(peakLeft, controller.left())
        }

        assertTrue("overshot Home: $peakLeft vs ${ltr[0].left}", peakLeft >= ltr[0].left - 0.001f)
        assertEquals(ltr[0].left, controller.left(), 0.6f)
    }

    @Test
    fun aDragPastEitherEndStillStopsAtHomeAndMore() = runTest {
        val controller = controller()

        controller.dragTo(-0.4f)
        assertEquals(ltr[0].left, controller.left(), 0.001f)
        controller.dragTo(3.6f)
        assertEquals(ltr[3].right, controller.right(), 0.001f)
    }

    @Test
    fun anInteriorTabStillBouncesButOnlyWithinTheOvershootBoundInTheDirectionOfTravel() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_120)
        val controller = controller()
        controller.snapTo(0f)
        controller.select(2)

        fly(driver, controller)
        runCurrent()
        var peakRight = 0f
        repeat(400) {
            driver.step()
            peakRight = maxOf(peakRight, controller.right())
        }
        assertTrue("no bounce on an interior tab: $peakRight", peakRight > ltr[2].right + 0.5f)
        assertTrue("bounce $peakRight past ${ltr[2].right} exceeded the ${overshoot}px bound", peakRight <= ltr[2].right + overshoot + 0.001f)
        assertTrue("the bounce left the pill: $peakRight", peakRight <= interior.right)

        // And back to the left, towards an interior tab.
        controller.select(1)
        fly(driver, controller)
        runCurrent()
        var peakLeft = Float.MAX_VALUE
        repeat(400) {
            driver.step()
            peakLeft = minOf(peakLeft, controller.left())
        }
        assertTrue("no bounce to the left: $peakLeft", peakLeft < ltr[1].left - 0.5f)
        assertTrue("left bounce $peakLeft past ${ltr[1].left} exceeded the ${overshoot}px bound", peakLeft >= ltr[1].left - overshoot - 0.001f)
    }

    @Test
    fun aShortHopBouncesOnlyAFewPixels() = runTest {
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller()
        controller.snapTo(0f)
        controller.select(1)

        fly(driver, controller)
        runCurrent()
        var peak = 0f
        repeat(120) {
            driver.step()
            peak = maxOf(peak, controller.right() - ltr[1].right)
        }

        assertTrue("short hop overshoot $peak", peak in 0f..4f)
    }

    @Test
    fun theCapsuleUsesPhysicalSlotPositionsSoRtlMirrorsForFree() = runTest {
        val rtl = slots(228f, 154f, 80f, 6f)
        val driver = FrameDriver(this, FrameDriver.HZ_60)
        val controller = controller(rtl)
        controller.select(0)
        controller.moveTo(controller.targetIndex, navSpring)

        // Index 0 is still the first logical tab, but it is physically on the right in RTL.
        assertEquals(rtl[0].left, controller.left(), 0.001f)
        controller.select(3)
        fly(driver, controller)
        runCurrent()
        var peakLeft = Float.MAX_VALUE
        repeat(300) {
            driver.step()
            peakLeft = minOf(peakLeft, controller.left())
        }
        assertTrue("RTL overshot the physical left end (More): $peakLeft", peakLeft >= rtl[3].left - 0.001f)
        assertEquals(rtl[3].left, controller.left(), 0.6f)

        // Back towards the physically rightmost slot (Home in RTL): no overshoot past that end either.
        controller.select(0)
        fly(driver, controller)
        runCurrent()
        var peakRight = 0f
        repeat(300) {
            driver.step()
            peakRight = maxOf(peakRight, controller.right())
        }
        assertTrue("RTL overshot the physical right end (Home): $peakRight", peakRight <= rtl[0].right + 0.001f)
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

        controller.snapTo(3f)

        assertEquals(ltr[3].left, controller.left(), 0.001f)
        assertFalse(controller.isMoving)
    }

    @Test
    fun remeasurementKeepsTheSelectionAndTheCapsuleFollowsItsNewSlot() = runTest {
        val controller = controller()
        controller.select(3)
        controller.snapTo(3f)

        // A font-size or rotation change moves the slots; the same destination stays selected.
        val moved = ltr.map { Rect(it.left + 10f, it.top, it.right + 10f, it.bottom) }
        controller.onSlotsMeasured(moved, interior.translate(10f, 0f))

        assertEquals(3, controller.targetIndex)
        assertEquals(moved[3], controller.targetRect)
        assertEquals(moved[3].left, controller.left(), 0.001f)
    }

    @Test
    fun theCapsuleNeverDrawsOutsideTheMeasuredBounds() = runTest {
        val controller = controller()
        controller.snapTo(-8f)

        val bounds = controller.capsuleBounds()!!

        assertTrue(bounds.left >= interior.left)
        assertTrue(bounds.left >= ltr[0].left - overshoot)
    }
}
