package com.example.zar.features.settings.domain.usecase

import com.example.zar.core.domain.usecase.resultOf
import com.example.zar.features.settings.domain.model.AppearanceMode
import com.example.zar.features.settings.domain.model.DisplayPreferences
import com.example.zar.features.settings.domain.repository.DisplayPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The device-local display choices, now and as they change. */
class ObserveDisplayPreferencesUseCase @Inject constructor(
    private val repository: DisplayPreferencesRepository,
) {
    fun current(): DisplayPreferences = repository.current()

    operator fun invoke(): Flow<DisplayPreferences> = repository.observe()
}

/** Chooses Light, Dark or System. A failure leaves the previous choice in effect. */
class SetAppearanceUseCase @Inject constructor(
    private val repository: DisplayPreferencesRepository,
) {
    suspend operator fun invoke(mode: AppearanceMode): Result<Unit> = resultOf { repository.setAppearance(mode) }
}

/** Turns the app's Reduce motion switch on or off. A failure leaves the previous choice in effect. */
class SetReduceMotionUseCase @Inject constructor(
    private val repository: DisplayPreferencesRepository,
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> = resultOf { repository.setReduceMotion(enabled) }
}
