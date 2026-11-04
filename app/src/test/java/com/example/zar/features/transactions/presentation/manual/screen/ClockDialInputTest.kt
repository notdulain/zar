package com.example.zar.features.transactions.presentation.manual.screen

import com.example.zar.core.presentation.haptics.SelectionHapticSink
import com.example.zar.core.presentation.haptics.SelectionTicker
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockDialInputTest {
    private val config = ClockDialConfig(armLength = 100f, centerDeadzone = 24f, armGrabRadius = 24f)

    private class Recorder(var detent: Int, var restingAngle: Float = detent * 30f) {
        val selected = mutableListOf<Int>()
        var finished = 0
        var armAngle: Float? = null
        var ticks = 0
        val ticker = SelectionTicker(SelectionHapticSink { ticks++ })
    }

    private fun input(rec: Recorder) = ClockDialInput(
        config = config,
        restingArmAngle = { rec.restingAngle },
        currentDetent = { rec.detent },
        onSelectDetent = { rec.selected += it; rec.detent = it; rec.restingAngle = it * 30f },
        onFinished = { rec.finished++ },
        ticks = rec.ticker,
        onArmAngle = { rec.armAngle = it },
    )

    /** Pointer at [angle] degrees clockwise from 12, [radius] from the dial centre. */
    private fun at(angle: Float, radius: Float = 100f): Pair<Float, Float> {
        val r = angle * PI / 180
        return (radius * sin(r)).toFloat() to (-radius * cos(r)).toFloat()
    }

    /** Moves the pointer; unless [atMillis] is given, 100ms pass so the 40ms tick cap never interferes. */
    private fun ClockDialInput.moveTo(angle: Float, radius: Float = 100f, atMillis: Long? = null) {
        clock += 100
        val (x, y) = at(angle, radius)
        dragMove(x, y, atMillis ?: clock)
    }

    private var clock = 1_000L

    @Test
    fun angleIsClockwiseFromTwelveAndWraps() {
        assertEquals(0f, ClockGeometry.angleOf(0f, -10f), 0.01f)
        assertEquals(90f, ClockGeometry.angleOf(10f, 0f), 0.01f)
        assertEquals(180f, ClockGeometry.angleOf(0f, 10f), 0.01f)
        assertEquals(270f, ClockGeometry.angleOf(-10f, 0f), 0.01f)
        assertEquals(11, ClockGeometry.detentOf(335f))
        assertEquals(0, ClockGeometry.detentOf(359f))
        assertEquals(0, ClockGeometry.detentOf(14f))
        assertEquals(1, ClockGeometry.detentOf(16f))
        assertEquals(20f, ClockGeometry.distance(350f, 10f), 0.01f)
    }

    @Test
    fun tapSelectsSectorAndFinishesOnce() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        val (x, y) = at(92f)
        i.press(x, y)
        i.tap(x, y, 1_000L)
        assertEquals(listOf(3), rec.selected)
        assertEquals(1, rec.finished)
    }

    @Test
    fun tapInCentreIsIgnored() {
        val rec = Recorder(detent = 4)
        val i = input(rec)
        i.press(3f, 3f)
        i.tap(3f, 3f, 1_000L)
        assertTrue(rec.selected.isEmpty())
        assertEquals(0, rec.finished)
    }

    @Test
    fun tappingCurrentDetentStillAppliesItsExactValue() {
        val rec = Recorder(detent = 5, restingAngle = 162f) // typed minute 27
        val i = input(rec)
        val (x, y) = at(150f)
        i.press(x, y)
        i.tap(x, y, 1_000L)
        assertEquals(listOf(5), rec.selected)
        assertEquals(1, rec.finished)
    }

    @Test
    fun dragAcrossSeveralDetentsTracksArmAndFinishesOnce() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        val (x, y) = at(2f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        listOf(20f, 50f, 80f, 100f).forEach { i.moveTo(it) }
        assertEquals(listOf(1, 2, 3), rec.selected)
        assertEquals(98f, rec.armAngle!!, 0.01f) // arm follows the pointer (minus the 2 degree grab offset), not the detent
        assertEquals(0, rec.finished)
        i.dragEnd()
        assertEquals(1, rec.finished)
        assertNull(rec.armAngle)
    }

    @Test
    fun grabbingTheArmKeepsItsOffsetSoThereIsNoJump() {
        val rec = Recorder(detent = 3, restingAngle = 90f)
        val i = input(rec)
        // Grab the arm 40dp out, a little off its line (about 5 degrees past).
        val (x, y) = at(95f, radius = 40f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        assertEquals(90f, rec.armAngle!!, 0.5f)
        assertTrue(rec.selected.isEmpty())
        i.moveTo(125f, radius = 40f)
        assertEquals(120f, rec.armAngle!!, 0.5f)
    }

    @Test
    fun pressingAwayFromTheArmSnapsToThePointer() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        val (x, y) = at(180f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        assertEquals(180f, rec.armAngle!!, 0.5f)
        assertEquals(listOf(6), rec.selected)
    }

    @Test
    fun hysteresisPreventsBoundaryFlicker() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        val (x, y) = at(0f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        // 15 is the raw boundary; jitter 14..17 around it must not change the selection.
        listOf(14f, 16f, 15f, 17f, 14.5f).forEach { i.moveTo(it) }
        assertTrue(rec.selected.isEmpty())
        i.moveTo(18.5f)
        assertEquals(listOf(1), rec.selected)
        // Jitter back across the raw boundary from detent 1 stays put until 3 degrees clear.
        listOf(16f, 14f, 13f).forEach { i.moveTo(it) }
        assertEquals(listOf(1), rec.selected)
        i.moveTo(11.5f)
        assertEquals(listOf(1, 0), rec.selected)
    }

    @Test
    fun wrapAroundFiftyFiveToZeroIsValid() {
        val rec = Recorder(detent = 11, restingAngle = 330f) // minute 55
        val i = input(rec)
        val (x, y) = at(330f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        i.moveTo(350f)
        i.moveTo(5f)
        i.moveTo(20f)
        assertEquals(listOf(0, 1), rec.selected)
        i.moveTo(350f)
        i.moveTo(325f)
        assertEquals(listOf(0, 1, 0, 11), rec.selected)
        assertTrue(rec.selected.all { it in 0..11 })
    }

    @Test
    fun centreNoiseDoesNotMoveTheSelection() {
        val rec = Recorder(detent = 3)
        val i = input(rec)
        val (x, y) = at(90f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        // Jittering around the centre within the dead zone would otherwise whip through every angle.
        listOf(0f, 90f, 180f, 270f, 45f).forEach { i.moveTo(it, radius = 10f) }
        assertTrue(rec.selected.isEmpty())
        assertEquals(90f, rec.armAngle!!, 0.5f)
    }

    @Test
    fun cancellationKeepsTheDraftAndDoesNotAdvance() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        val (x, y) = at(0f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        i.moveTo(70f)
        i.dragCancel()
        assertEquals(listOf(2), rec.selected)
        assertEquals(2, rec.detent)
        assertEquals(0, rec.finished)
        assertNull(rec.armAngle)
        // Stray release after cancellation must not finish either.
        i.dragEnd()
        assertEquals(0, rec.finished)
    }

    @Test
    fun movesBeforeDragStartAreIgnored() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        i.moveTo(120f)
        assertTrue(rec.selected.isEmpty())
        assertNull(rec.armAngle)
    }

    @Test
    fun releasingOnTheSameDetentDoesNotReselect() {
        val rec = Recorder(detent = 2)
        val i = input(rec)
        val (x, y) = at(60f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        i.moveTo(62f)
        i.moveTo(58f)
        i.dragEnd()
        assertTrue(rec.selected.isEmpty())
        assertEquals(1, rec.finished)
    }

    // Haptics: one tick per user-changed detent, capped at one per 40ms, never queued.

    @Test
    fun slowDragTicksOncePerCrossedDetentAndNotWhileStationaryOrOnRelease() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        val (x, y) = at(0f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        assertEquals(0, rec.ticks)
        i.moveTo(40f)
        i.moveTo(70f)
        i.moveTo(100f)
        assertEquals(listOf(1, 2, 3), rec.selected)
        assertEquals(3, rec.ticks)
        // Holding inside the last sector: repeated samples, then release.
        repeat(10) { i.moveTo(101f) }
        i.dragEnd()
        assertEquals(3, rec.ticks)
    }

    @Test
    fun returningToAPreviousDetentTicksAgain() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        val (x, y) = at(0f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        i.moveTo(40f)
        i.moveTo(0f)
        assertEquals(listOf(1, 0), rec.selected)
        assertEquals(2, rec.ticks)
    }

    @Test
    fun fastScrubUpdatesEverySelectionButCapsPhysicalTicks() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        val (x, y) = at(0f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        // One detent every 10ms across a full circle: visuals follow every one, ticks only every 40ms.
        for (step in 1..12) i.moveTo(step * 30f, atMillis = 1_000L + step * 10L)
        assertEquals((1..11).toList() + 0, rec.selected) // every detent is selected, none coalesced
        assertEquals(3, rec.ticks) // steps at 10, 50 and 90ms; 12 steps span 120ms
    }

    @Test
    fun skippedTicksAreNeverReplayedAfterTheGesture() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        val (x, y) = at(0f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        i.moveTo(40f, atMillis = 1_010L)
        i.moveTo(70f, atMillis = 1_020L) // inside the cap: no tick, no backlog
        val afterBurst = rec.ticks
        i.dragEnd()
        assertEquals(afterBurst, rec.ticks)
        assertEquals(1, rec.ticks)
    }

    @Test
    fun tapOnADifferentDetentTicksAndOnTheSameDetentIsSilent() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        val (x, y) = at(92f)
        i.press(x, y)
        i.tap(x, y, 1_000L)
        assertEquals(1, rec.ticks)
        i.press(x, y)
        i.tap(x, y, 2_000L)
        assertEquals(1, rec.ticks)
        assertEquals(listOf(3, 3), rec.selected)
    }

    @Test
    fun accessibilityClickSelectsWithoutOurTick() {
        val rec = Recorder(detent = 0)
        val i = input(rec)
        i.clickDetent(4)
        assertEquals(listOf(4), rec.selected)
        assertEquals(1, rec.finished)
        assertEquals(0, rec.ticks)
    }

    @Test
    fun dragMovesInsideTheCentreNeverTick() {
        val rec = Recorder(detent = 3)
        val i = input(rec)
        val (x, y) = at(90f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        listOf(0f, 180f, 270f).forEach { i.moveTo(it, radius = 8f) }
        assertEquals(0, rec.ticks)
    }

    // Minute mode: 60 detents, so every minute is selectable and ticks.

    private val minuteConfig = ClockDialConfig(armLength = 100f, centerDeadzone = 24f, armGrabRadius = 24f, hysteresisDegrees = 1f, detents = 60)

    private fun minuteInput(rec: Recorder) = ClockDialInput(
        config = minuteConfig,
        restingArmAngle = { rec.restingAngle },
        currentDetent = { rec.detent },
        onSelectDetent = { rec.selected += it; rec.detent = it; rec.restingAngle = it * 6f },
        onFinished = { rec.finished++ },
        ticks = rec.ticker,
        onArmAngle = { rec.armAngle = it },
    )

    @Test
    fun minuteGeometryHasSixDegreeDetents() {
        assertEquals(17, ClockGeometry.detentOf(100f, 60))
        assertEquals(0, ClockGeometry.detentOf(359f, 60))
        assertEquals(1, ClockGeometry.detentOf(3.5f, 60))
        assertEquals(102f, ClockGeometry.angleOfDetent(17, 60), 0.001f)
    }

    @Test
    fun tapSelectsTheExactMinuteNotTheNearestFive() {
        val rec = Recorder(detent = 0)
        val i = minuteInput(rec)
        val (x, y) = at(100f)
        i.press(x, y)
        i.tap(x, y, 1_000L)
        assertEquals(listOf(17), rec.selected)
        assertEquals(1, rec.finished)
        assertEquals(1, rec.ticks)
    }

    @Test
    fun slowMinuteDragSelectsAndTicksEveryMinute() {
        val rec = Recorder(detent = 0, restingAngle = 0f)
        val i = minuteInput(rec)
        val (x, y) = at(0f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        for (minute in 1..5) i.moveTo(minute * 6f)
        assertEquals(listOf(1, 2, 3, 4, 5), rec.selected)
        assertEquals(5, rec.ticks)
        i.moveTo(0f)
        assertEquals(0, rec.selected.last())
        assertEquals(6, rec.ticks)
    }

    @Test
    fun minuteHysteresisStaysUnderHalfAMinuteSoTheArmDoesNotLag() {
        val rec = Recorder(detent = 0, restingAngle = 0f)
        val i = minuteInput(rec)
        val (x, y) = at(0f)
        i.press(x, y)
        i.dragStart(x, y, 1_000L)
        i.moveTo(3.5f) // 0.5 degrees past the 3 degree boundary: no switch yet
        assertTrue(rec.selected.isEmpty())
        i.moveTo(4.5f) // 1.5 degrees past: switches to minute 1
        assertEquals(listOf(1), rec.selected)
    }
}
