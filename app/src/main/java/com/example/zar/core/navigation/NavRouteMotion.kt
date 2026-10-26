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

    /** What a route change does to the screen it moves. The NavHost maps each to a concrete Compose transition. */
    enum class Move {
        /** Reduced motion: a crossfade of at most 100 ms, or nothing when system animations are off. */
        Fade,

        /** Peer tab travel toward the end of Home / Activity / Plans / More; shared chrome stays put. */
        PeerTowardEnd,

        /** Peer tab travel toward the start. */
        PeerTowardStart,

        /** A child enters from the logical end. */
        PushIn,

        /** The parent recedes a little toward the logical start while a child covers it. */
        Recede,

        /** Back/Up reveals the parent from the logical start side. */
        PopIn,

        /** The child leaves toward the logical end. */
        PopOut,

        /** A sheet-style route enters from below. */
        ModalUp,

        /** A sheet-style route leaves downward, the way it came. */
        ModalDown,

        /** The screen stays where it is: the parent beneath a sheet. */
        Hold,
    }

    private fun peer(from: TopLevelDestination, to: TopLevelDestination) =
        if (tabDirection(from, to) > 0) Move.PeerTowardEnd else Move.PeerTowardStart

    /** The incoming screen of a forward navigation. [targetModal] is true for a sheet-style destination. */
    fun enter(spatial: Boolean, from: TopLevelDestination?, to: TopLevelDestination?, targetModal: Boolean): Move = when {
        !spatial -> Move.Fade
        isPeerTab(from, to) -> peer(from!!, to!!)
        targetModal -> Move.ModalUp
        else -> Move.PushIn
    }

    /** The outgoing screen of a forward navigation. Nothing recedes behind a sheet. */
    fun exit(spatial: Boolean, from: TopLevelDestination?, to: TopLevelDestination?, targetModal: Boolean): Move = when {
        !spatial -> Move.Fade
        isPeerTab(from, to) -> peer(from!!, to!!)
        targetModal -> Move.Hold
        else -> Move.Recede
    }

    /** The screen revealed by Back. Under a sheet it was never moved, so it holds. */
    fun popEnter(spatial: Boolean, from: TopLevelDestination?, to: TopLevelDestination?, initialModal: Boolean): Move = when {
        !spatial -> Move.Fade
        isPeerTab(from, to) -> peer(from!!, to!!)
        initialModal -> Move.Hold
        else -> Move.PopIn
    }

    /** The screen Back dismisses. [initialModal] is true when it is a sheet-style destination. */
    fun popExit(spatial: Boolean, from: TopLevelDestination?, to: TopLevelDestination?, initialModal: Boolean): Move = when {
        !spatial -> Move.Fade
        isPeerTab(from, to) -> peer(from!!, to!!)
        initialModal -> Move.ModalDown
        else -> Move.PopOut
    }
}
