package com.example.zar.core.navigation

/** Where a launch from outside the app (for example a notification) should take the person. */
object LaunchTargets {
    const val EXTRA = "zar.launch_target"

    /** The weekly catch-up reminder. */
    const val CATCH_UP = "catch_up"

    /** A notification for one captured bank message; the draft's id is in [EXTRA_DRAFT_ID]. */
    const val CAPTURE_REVIEW = "capture_review"
    const val EXTRA_DRAFT_ID = "zar.draft_id"
}
