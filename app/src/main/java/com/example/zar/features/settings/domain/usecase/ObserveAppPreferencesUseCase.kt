package com.example.zar.features.settings.domain.usecase

import com.example.zar.features.settings.domain.model.AppPreferences
import com.example.zar.features.settings.domain.repository.AppPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveAppPreferencesUseCase @Inject constructor(
    private val repository: AppPreferencesRepository,
) {
    operator fun invoke(): Flow<AppPreferences> = repository.observePreferences()
}
