package com.example.zar.features.settings

import com.example.zar.features.settings.domain.model.AppearanceMode
import com.example.zar.features.settings.domain.model.DisplayPreferences
import com.example.zar.features.settings.domain.repository.DisplayPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An in-memory [DisplayPreferencesRepository]; set [failing] to make writes fail the way a full disk would. */
class FakeDisplayPreferences(initial: DisplayPreferences = DisplayPreferences()) : DisplayPreferencesRepository {
    private val state = MutableStateFlow(initial)
    var failing = false

    override fun current(): DisplayPreferences = state.value
    override fun observe(): Flow<DisplayPreferences> = state.asStateFlow()

    override suspend fun setAppearance(mode: AppearanceMode) {
        if (failing) throw IOException("cannot save")
        state.value = state.value.copy(appearance = mode)
    }
}
