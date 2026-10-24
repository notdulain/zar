package com.example.zar.features.allowance

import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.model.AllowanceSyncState
import com.example.zar.features.allowance.domain.repository.AllowanceRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class FakeAllowanceRepository(initial: AllowanceSettings = AllowanceSettings.Disabled) : AllowanceRepository {
    val settings = MutableStateFlow(initial)
    val syncState = MutableStateFlow(AllowanceSyncState.SAVED)
    var saveGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var failReads = false
    var failSaves = false
    var saveCount = 0

    override fun observeSettings(): Flow<AllowanceSettings> =
        if (failReads) flow { throw IOException("allowance unavailable") } else settings

    override fun observeSyncState(): Flow<AllowanceSyncState> = syncState

    override suspend fun saveSettings(settings: AllowanceSettings) {
        if (failSaves) throw IOException("rejected")
        saveGate?.await()
        saveCount++
        this.settings.value = settings
    }
}
