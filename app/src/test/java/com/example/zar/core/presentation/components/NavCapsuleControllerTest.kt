package com.example.zar.core.presentation.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The retained navbar mover: one capsule that snaps to the real selection on first reveal, then interpolates between
 * the measured slots and retargets from wherever it currently is.
 */
class NavCapsuleControllerTest {

    private fun slots(vararg lefts: Float, width: Float = 72f, top: Float = 4f, height: Float = 59f) =
        lefts.map { Rect(it, top, it + width, top + height) }

    private val ltr = slots(6f, 80f, 154f, 228f)

    @Test
    fun beforeMeasurementThereIsNoCapsule() {
        val controller = NavCapsuleController()

        assertNull(controller.capsuleBounds())
        assertFalse(controller.revealed)
        assertEquals(0, controller.slotCount)
    }

    @Test
    fun firstRevealSnapsToTheActualSelectionWithoutFlying() {
        val controller = NavCapsuleController()
        // The user is already on Activity (index 1) when the navbar first appears.
        controller.select(2)

        controller.onSlotsMeasured(ltr)

        assertTrue(controller.revealed)
        assertEquals(2, controller.targetIndex)
        assertEquals(2f, controller.presentationIndex, 0f)
        assertEquals(ltr[2], controller.capsuleBounds())
    }

    @Test
    fun theCapsuleInterpolatesBetweenMeasuredSlots() {
        val controller = NavCapsuleController()
        controller.onSlotsMeasured(ltr)
        controller.select(1)
        controller.setPresentation(1.5f)

        val bounds = controller.capsuleBounds()!!

        assertEquals((ltr[1].left + ltr[2].left) / 2f, bounds.left, 0.001f)
        assertEquals((ltr[1].right + ltr[2].right) / 2f, bounds.right, 0.001f)
        assertEquals((ltr[1].top + ltr[2].top) / 2f, bounds.top, 0.001f)
    }

    @Test
    fun aNewSelectionRetargetsFromTheCurrentPresentationInsteadOfRestarting() {
        val controller = NavCapsuleController()
        controller.onSlotsMeasured(ltr)
        controller.setPresentation(0f)
        controller.select(3)
        // Mid-flight the user picks Home; the capsule retargets from where it is, it does not queue.
        controller.setPresentation(1.25f)
        controller.select(0)

        assertEquals(0, controller.targetIndex)
        assertEquals(1.25f, controller.presentationIndex, 0f)
        val bounds = controller.capsuleBounds()!!
        assertTrue(bounds.left > ltr[0].left && bounds.left < ltr[2].left)
    }

    @Test
    fun theCapsuleNeverLeavesTheEndpointSlotsEvenIfTheSpringOvershoots() {
        val controller = NavCapsuleController()
        controller.onSlotsMeasured(ltr)

        controller.setPresentation(-0.4f)
        assertEquals(ltr[0], controller.capsuleBounds())

        controller.setPresentation(3.6f)
        assertEquals(ltr[3], controller.capsuleBounds())
    }

    @Test
    fun theCapsuleUsesPhysicalSlotPositionsSoRtlMirrorsForFree() {
        val rtl = slots(228f, 154f, 80f, 6f)
        val controller = NavCapsuleController()
        controller.onSlotsMeasured(rtl)
        controller.select(0)

        // Index 0 is still the first logical tab, but it is physically on the right in RTL.
        assertEquals(rtl[0], controller.capsuleBounds())
        controller.setPresentation(1f)
        assertEquals(rtl[1], controller.capsuleBounds())
    }

    @Test
    fun selectionIsClampedToTheMeasuredSlots() {
        val controller = NavCapsuleController()
        controller.onSlotsMeasured(ltr)

        controller.select(99)

        assertEquals(3, controller.targetIndex)
    }

    @Test
    fun snapToTargetResolvesImmediatelyForReducedMotion() {
        val controller = NavCapsuleController()
        controller.onSlotsMeasured(ltr)
        controller.select(3)

        controller.snapToTarget()

        assertEquals(3f, controller.presentationIndex, 0f)
        assertEquals(ltr[3], controller.capsuleBounds())
    }

    @Test
    fun remeasurementKeepsTheSelectionAndClampsThePresentation() {
        val controller = NavCapsuleController()
        controller.onSlotsMeasured(ltr)
        controller.select(3)
        controller.setPresentation(3f)

        controller.onSlotsMeasured(ltr.map { Rect(it.left, it.top, it.right, it.bottom) })

        assertEquals(3, controller.targetIndex)
        assertEquals(3f, controller.presentationIndex, 0f)
    }
}
