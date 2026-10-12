package com.example.zar.features.settings.domain.usecase

import com.example.zar.features.settings.domain.model.AppPreferences
import com.example.zar.features.settings.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveAppPreferencesUseCaseTest {

    private val fakeRepository = object : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = flowOf(AppPreferences(currencyCode = "LKR"))
    }

    @Test
    fun returnsPreferencesFromRepository() = runBlocking {
        val preferences = ObserveAppPreferencesUseCase(fakeRepository)().first()

        assertEquals("LKR", preferences.currencyCode)
    }
}
