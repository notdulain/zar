package com.example.zar.features.settings.domain.repository

import com.example.zar.features.settings.domain.model.AppPreferences
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>
}
