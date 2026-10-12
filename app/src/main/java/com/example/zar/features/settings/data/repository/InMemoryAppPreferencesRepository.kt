package com.example.zar.features.settings.data.repository

import com.example.zar.features.settings.domain.model.AppPreferences
import com.example.zar.features.settings.domain.repository.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Reference implementation until preferences are persisted (DataStore/Firestore).
@Singleton
class InMemoryAppPreferencesRepository @Inject constructor() : AppPreferencesRepository {
    private val preferences = MutableStateFlow(AppPreferences(currencyCode = "LKR"))

    override fun observePreferences(): Flow<AppPreferences> = preferences.asStateFlow()
}
