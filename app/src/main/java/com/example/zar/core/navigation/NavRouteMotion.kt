package com.example.zar.core.navigation

import com.example.zar.core.presentation.components.TopLevelDestination

/**
 * Classifies a navigation change so the NavHost can pick the right path. Kept pure and separate from the Compose
 * transitions so the direction rules are unit-testable without a device.
 *
 * A change between two top-level tabs is a peer switch with a short directional travel; anything else is a
 * hierarchical push/pop where the child enters from the logical end and the parent may recede. Reduced motion
 * collapses every case to an immediate change.
 */
internal object NavRouteMotion {

    /** True when both the outgoing and incoming destinations are top-level tabs, so the shared chrome stays put. */
    fun isPeerTab(from: TopLevelDestination?, to: TopLevelDestination?): Boolean = from != null && to != null

    /**
     * The logical travel direction between two tabs: `+1` toward the end of Home / Activity / Plans / More order,
     * `-1` toward the start. The transition mirrors this in RTL because it uses logical Start/End.
     */
    fun tabDirection(from: TopLevelDestination, to: TopLevelDestination): Int =
        if (to.ordinal >= from.ordinal) 1 else -1
}
