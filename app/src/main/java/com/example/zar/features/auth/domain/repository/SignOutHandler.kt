package com.example.zar.features.auth.domain.repository

/**
 * Runs before the user is signed out. Features that work in the background for the current
 * user (e.g. SMS capture) register one with Hilt `@IntoSet` to pause and drop that user's state.
 */
fun interface SignOutHandler {
    suspend fun onSignOut()
}
