package com.example.zar.features.allowance.domain.repository

import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.model.AllowanceSyncState
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in user's weekly allowance setting. Nothing saved means the allowance is off.
 * Implementations must keep working from Firestore's offline cache.
 */
interface AllowanceRepository {
    fun observeSettings(): Flow<AllowanceSettings>
    fun observeSyncState(): Flow<AllowanceSyncState>

    /** Creates or replaces the one setting. Returns once stored locally (offline first). */
    suspend fun saveSettings(settings: AllowanceSettings)
}
