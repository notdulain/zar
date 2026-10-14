package com.example.zar.core.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation destinations. Add one entry per top-level screen.

// Signed-out
@Serializable
data object SignInRoute

@Serializable
data object RegisterRoute

// Signed-in
@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute
