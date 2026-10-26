package com.example.zar.core.navigation

import com.example.zar.core.presentation.components.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavRouteMotionTest {

    @Test
    fun aChangeBetweenTwoTabsIsAPeerSwitch() {
        assertTrue(NavRouteMotion.isPeerTab(TopLevelDestination.Home, TopLevelDestination.Plans))
        assertTrue(NavRouteMotion.isPeerTab(TopLevelDestination.More, TopLevelDestination.Home))
    }

    @Test
    fun aChildOrUnknownDestinationIsNotAPeerSwitch() {
        assertFalse(NavRouteMotion.isPeerTab(null, TopLevelDestination.Home))
        assertFalse(NavRouteMotion.isPeerTab(TopLevelDestination.Home, null))
        assertFalse(NavRouteMotion.isPeerTab(null, null))
    }

    @Test
    fun travelDirectionFollowsHomeActivityPlansMoreOrder() {
        assertEquals(1, NavRouteMotion.tabDirection(TopLevelDestination.Home, TopLevelDestination.More))
        assertEquals(1, NavRouteMotion.tabDirection(TopLevelDestination.Activity, TopLevelDestination.Plans))
        assertEquals(-1, NavRouteMotion.tabDirection(TopLevelDestination.More, TopLevelDestination.Home))
        assertEquals(-1, NavRouteMotion.tabDirection(TopLevelDestination.Plans, TopLevelDestination.Activity))
    }

    @Test
    fun stayingOnTheSameTabHasNoBackwardTravel() {
        assertEquals(1, NavRouteMotion.tabDirection(TopLevelDestination.Plans, TopLevelDestination.Plans))
    }
}
