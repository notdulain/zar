package com.example.zar.core.presentation.haptics

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionHapticsTest {
    private var ticks = 0
    private val ticker = SelectionTicker(SelectionHapticSink { ticks++ })

    @Test
    fun firstTickAlwaysFires() {
        assertTrue(ticker.tick(0L))
        assertEquals(1, ticks)
    }

    @Test
    fun ticksUnderFortyMillisecondsApartAreDroppedNotQueued() {
        assertTrue(ticker.tick(1_000L))
        assertFalse(ticker.tick(1_010L))
        assertFalse(ticker.tick(1_039L))
        assertEquals(1, ticks)
        assertTrue(ticker.tick(1_040L))
        assertEquals(2, ticks)
    }

    @Test
    fun droppedTicksDoNotPushTheWindowForward() {
        assertTrue(ticker.tick(1_000L))
        assertFalse(ticker.tick(1_030L))
        assertTrue(ticker.tick(1_041L)) // measured from the last physical tick, not the dropped one
    }

    @Test
    fun clockGoingBackwardsDoesNotWedgeTheTicker() {
        assertTrue(ticker.tick(5_000L))
        assertTrue(ticker.tick(100L))
    }

    @Test
    fun usesSegmentFrequentTickFromApi34AndClockTickBefore() {
        assertEquals(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, selectionTickConstant(34))
        assertEquals(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, selectionTickConstant(36))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, selectionTickConstant(33))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, selectionTickConstant(26))
    }
}
