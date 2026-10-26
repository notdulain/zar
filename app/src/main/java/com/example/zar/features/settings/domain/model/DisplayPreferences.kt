package com.example.zar.features.settings.domain.model

/**
 * How this phone shows the app. These belong to the device, not the account: they are not synced, are not
 * restored from a backup onto another phone, and stay in place when someone signs out or another user signs in.
 */
data class DisplayPreferences(
    val appearance: AppearanceMode = AppearanceMode.Default,
)
