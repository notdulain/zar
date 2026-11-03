package com.example.zar.core.presentation.components

import com.example.zar.core.presentation.motion.MotionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedDialPoseTest {
    @Test
    fun homeRowKeepsLogicalStartDespitePixelRoundingWhileFullNavOpensInward() {
        for (center in listOf(499.5f, 500f, 500.5f)) {
            assertEquals(-1f, speedDialRowDirection(true, false, center, 500f), 0f)
            assertEquals(1f, speedDialRowDirection(true, true, center, 500f), 0f)
        }
        assertEquals(-1f, speedDialRowDirection(false, false, 900f, 500f), 0f)
        assertEquals(1f, speedDialRowDirection(false, true, 100f, 500f), 0f)
    }

    @Test
    fun matchesTheDesignFixtureAcrossOpeningClosingAndReversal() {
        // rotation, Quick travel/scale/alpha, Manual travel/scale/alpha (verification.md).
        val rows = listOf(
            floatArrayOf(0f, 0f, .720f, 0f, 0f, .720f, 0f),
            floatArrayOf(5.96f, 18f, .757f, .38f, 0f, .720f, 0f),
            floatArrayOf(17.43f, 52.7f, .828f, 1f, 18.2f, .791f, .72f),
            floatArrayOf(28.93f, 87.4f, .900f, 1f, 40.6f, .878f, 1f),
            floatArrayOf(40.44f, 122.2f, .972f, 1f, 63.1f, .965f, 1f),
            floatArrayOf(46.69f, 141.1f, 1f, 1f, 74.7f, 1f, 1f),
            floatArrayOf(44.99f, 136f, 1f, 1f, 72f, 1f, 1f),
            floatArrayOf(36.27f, 109.6f, .946f, 1f, 55f, .934f, 1f),
            floatArrayOf(23.49f, 71f, .866f, 1f, 30f, .837f, 1f),
            floatArrayOf(13.81f, 41.7f, .806f, .88f, 11.1f, .763f, .44f),
            floatArrayOf(4.13f, 12.5f, .746f, .26f, 0f, .720f, 0f),
            floatArrayOf(34.40f, 104f, .934f, 1f, 51.3f, .920f, 1f),
            floatArrayOf(27.04f, 81.7f, .888f, 1f, 37f, .864f, 1f),
            floatArrayOf(13.16f, 39.8f, .802f, .84f, 9.9f, .758f, .39f),
            floatArrayOf(19.48f, 58.9f, .841f, 1f, 22.2f, .806f, .88f),
        )
        for (r in rows) {
            val pose = speedDialPose(r[0] / 45f, MotionLevel.Full, true)
            assertEquals(r[1], pose.quick.travel, .5f)
            assertEquals(r[2], pose.quick.scale, .005f)
            assertEquals(r[3], pose.quick.alpha, .02f)
            assertEquals(r[4], pose.manual.travel, .5f)
            assertEquals(r[5], pose.manual.scale, .005f)
            assertEquals(r[6], pose.manual.alpha, .02f)
        }
        for (sample in -5..105) {
            val pose = speedDialPose(sample / 100f, MotionLevel.Full, true)
            assertTrue(pose.quick.travel >= pose.manual.travel)
            assertTrue(pose.quick.travel <= 142f)
            assertTrue(pose.quick.scale <= 1f && pose.manual.alpha <= 1f)
        }
    }

    @Test
    fun reducedAndInstantKeepActionsAtRestAndStepTheGlyph() {
        for (level in listOf(MotionLevel.Reduced, MotionLevel.Instant)) {
            for (progress in listOf(0f, .2f, .7f, 1f)) {
                val pose = speedDialPose(progress, level, true)
                assertEquals(45f, pose.rotation, 0f)
                assertEquals(136f, pose.quick.travel, 0f)
                assertEquals(72f, pose.manual.travel, 0f)
                assertEquals(1f, pose.quick.scale, 0f)
                assertEquals(progress, pose.manual.alpha, 0f)
                assertEquals(0f, speedDialPose(progress, level, false).rotation, 0f)
            }
        }
        assertTrue(speedDialUsesRow(143f))
        assertTrue(!speedDialUsesRow(144f))
    }
}
