package com.example.zar.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Whether amounts are hidden on this phone. It is a device-local preference: it is not saved to
 * the user's account and is not restored from a backup onto another phone.
 */
interface AmountPrivacyRepository {
    /**
     * The saved choice, available straight away without waiting for storage. The app reads it
     * before the first screen is drawn, so a hidden amount is never shown for a moment first.
     */
    fun isHidden(): Boolean

    fun observeHidden(): Flow<Boolean>

    /** Takes effect at once; the choice is remembered for next time. */
    suspend fun setHidden(hidden: Boolean)
}
