package com.example.zar.core.navigation

/**
 * Classifies a navigation change so the NavHost can pick the right path. Kept pure and separate from the Compose
 * transitions so the direction rules are unit-testable without a device.
 *
 * The four tabs are pages of one pager inside a single destination (see `TabPagerHost`), so there is no peer-tab route
 * change any more: every change here is a hierarchical push/pop or a sheet. A child enters from the logical end and the
 * parent may recede; every Back, whatever surface it dismisses, slides the departing surface toward the logical end
 * (the right in LTR) with no zoom or fade. Reduced motion collapses every case to a short crossfade.
 */
internal object NavRouteMotion {

    /** What a route change does to the screen it moves. The NavHost maps each to a concrete Compose transition. */
    enum class Move {
        /** Reduced motion: a crossfade of at most 100 ms, or nothing when system animations are off. */
        Fade,

        /** A child enters from the logical end. */
        PushIn,

        /** The parent recedes a little toward the logical start while a child covers it. */
        Recede,

        /** Back/Up reveals the parent, coming in from a little behind the logical start as the child leaves. */
        PopIn,

        /** The child leaves toward the logical end. Every Back uses this, sheets included. */
        PopOut,

        /** A sheet-style route enters from below. */
        ModalUp,

        /** The screen stays where it is: the parent beneath a sheet. */
        Hold,
    }

    /** The incoming screen of a forward navigation. [targetModal] is true for a sheet-style destination. */
    fun enter(spatial: Boolean, targetModal: Boolean): Move = when {
        !spatial -> Move.Fade
        targetModal -> Move.ModalUp
        else -> Move.PushIn
    }

    /** The outgoing screen of a forward navigation. Nothing recedes behind a sheet. */
    fun exit(spatial: Boolean, targetModal: Boolean): Move = when {
        !spatial -> Move.Fade
        targetModal -> Move.Hold
        else -> Move.Recede
    }

    /** The screen revealed by Back. Under a sheet it was never moved, so it holds. */
    fun popEnter(spatial: Boolean, initialModal: Boolean): Move = when {
        !spatial -> Move.Fade
        initialModal -> Move.Hold
        else -> Move.PopIn
    }

    /** The screen Back dismisses. A sheet leaves the same way a page does: toward the logical end. */
    fun popExit(spatial: Boolean): Move = if (spatial) Move.PopOut else Move.Fade
}
