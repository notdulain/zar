package com.example.zar.features.allowance

import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.repository.AllowanceRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class FakeAllowanceRepository(initial: AllowanceSettings = AllowanceSettings.Disabled) : AllowanceRepository {
    val settings = MutableStateFlow(initial)
    var failReads = false
    var failSaves = false
    var saveCount = 0

    override fun observeSettings(): Flow<AllowanceSettings> =
        if (failReads) flow { throw IOException("allowance unavailable") } else settings

    override suspend fun saveSettings(settings: AllowanceSettings) {
        if (failSaves) throw IOException("rejected")
        saveCount++
        this.settings.value = settings
    }
}
