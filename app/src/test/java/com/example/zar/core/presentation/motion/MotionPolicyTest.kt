package com.example.zar.core.presentation.motion

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolicyTest {

    private val full = MotionPolicy.from(reduceMotionSetting = false, animatorDurationScale = 1f)
    private val reduced = MotionPolicy.from(reduceMotionSetting = true, animatorDurationScale = 1f)
    private val instant = MotionPolicy.from(reduceMotionSetting = false, animatorDurationScale = 0f)

    @Test
    fun effectiveReductionIsTheAppSwitchOrDisabledSystemAnimations() {
        assertEquals(MotionLevel.Full, full.level)
        assertEquals(MotionLevel.Reduced, reduced.level)
        assertEquals(MotionLevel.Instant, instant.level)
        // System animations off wins even when the app switch is also on.
        assertEquals(MotionLevel.Instant, MotionPolicy.from(reduceMotionSetting = true, animatorDurationScale = 0f).level)
    }

    @Test
    fun anyNonZeroAnimatorScaleKeepsAnimationsOn() {
        listOf(0.5f, 1f, 1.5f, 10f).forEach { scale ->
            assertTrue("scale $scale", MotionPolicy.from(false, scale).systemAnimationsEnabled)
        }
        assertFalse(MotionPolicy.from(false, 0f).systemAnimationsEnabled)
    }

    @Test
    fun spatialMotionAndPressScalingExistOnlyAtFullMotion() {
        assertTrue(full.allowsSpatialMotion)
        assertEquals(0.98f, full.pressScale, 0f)
        assertEquals(24.dp, full.travel(24.dp))
        listOf(reduced, instant).forEach {
            assertFalse(it.allowsSpatialMotion)
            assertEquals(1f, it.pressScale, 0f)
            assertEquals(0.dp, it.travel(24.dp))
        }
    }

    @Test
    fun springsMatchTheDesignTokensAtFullMotionAndSnapOtherwise() {
        val settle = full.spring<Float>(ZarSprings.Settle) as SpringSpec<Float>
        assertEquals(1f, settle.dampingRatio, 0f)
        assertEquals(400f, settle.stiffness, 0f)
        val nav = full.spring<Float>(ZarSprings.NavSelection) as SpringSpec<Float>
        assertEquals(0.75f, nav.dampingRatio, 0f)
        assertEquals(500f, nav.stiffness, 0f)
        val sheet = full.spring<Float>(ZarSprings.SheetSettle) as SpringSpec<Float>
        assertEquals(0.9f, sheet.dampingRatio, 0f)
        assertEquals(450f, sheet.stiffness, 0f)
        val route = full.spring<Float>(ZarSprings.Route) as SpringSpec<Float>
        assertEquals(1f, route.dampingRatio, 0f)
        assertEquals(500f, route.stiffness, 0f)

        listOf(reduced, instant).forEach { policy ->
            listOf(ZarSprings.Settle, ZarSprings.NavSelection, ZarSprings.SheetSettle, ZarSprings.Route).forEach {
                assertTrue(policy.spring<Float>(it) is SnapSpec<*>)
            }
        }
    }

    @Test
    fun routeAndSettleSpringsNeverOvershootAndOnlyTheNavCapsuleBounces() {
        assertTrue(ZarSprings.Settle.dampingRatio >= 1f)
        assertTrue(ZarSprings.Route.dampingRatio >= 1f)
        assertTrue(ZarSprings.NavSelection.dampingRatio < 1f)
    }

    @Test
    fun crossfadesAreCappedAt100msWhenReducedAndImmediateWhenSystemAnimationsAreOff() {
        assertEquals(160, (full.fade<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(300, (full.fade<Float>(300) as TweenSpec<Float>).durationMillis)
        assertEquals(100, (reduced.fade<Float>(300) as TweenSpec<Float>).durationMillis)
        assertEquals(60, (reduced.fade<Float>(60) as TweenSpec<Float>).durationMillis)
        assertTrue(instant.fade<Float>(300) is SnapSpec<*>)
    }

    @Test
    fun policiesWithTheSameInputsAreEqualSoTheyDoNotInvalidateTheTree() {
        assertEquals(MotionPolicy.from(true, 1f), MotionPolicy.from(true, 2f))
        assertEquals(MotionPolicy.from(true, 1f).hashCode(), MotionPolicy.from(true, 2f).hashCode())
        assertFalse(MotionPolicy.from(true, 1f) == MotionPolicy.from(false, 1f))
    }
}
