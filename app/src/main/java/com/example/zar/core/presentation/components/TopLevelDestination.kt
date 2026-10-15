package com.example.zar.core.presentation.components

import androidx.annotation.StringRes
import com.example.zar.R

/** The four main areas of the app. Home has no navbar; the others share the bottom navbar. */
enum class TopLevelDestination(@StringRes val label: Int) {
    Home(R.string.nav_home),
    Activity(R.string.nav_activity),
    Plans(R.string.nav_plans),
    More(R.string.nav_more),
}

/** What the user can pick in the Add sheet. Each feature owner wires the destination. */
enum class AddEntryOption { Voice, QuickNote, Manual }
