package com.example.zar.features.settings.domain.repository

import com.example.zar.features.settings.domain.model.AppearanceMode
import com.example.zar.features.settings.domain.model.DisplayPreferences
import kotlinx.coroutines.flow.Flow

/** The device-local display choices (see [DisplayPreferences]). */
interface DisplayPreferencesRepository {
    /**
     * The saved choices, available straight away without waiting for storage. The app reads them before the first
     * screen is drawn, so a saved appearance never flashes the other one first.
     */
    fun current(): DisplayPreferences

    fun observe(): Flow<DisplayPreferences>

    /**
     * Saves the choice and then publishes it. If the write fails this throws and the previous choice stays in
     * effect, so what is shown always matches what is stored.
     */
    suspend fun setAppearance(mode: AppearanceMode)
}
