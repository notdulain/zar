package com.example.zar.features.catchup.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.catchup.domain.ReminderSchedule
import com.example.zar.features.catchup.domain.model.ReminderSettings
import com.example.zar.features.catchup.domain.usecase.ObserveCatchUpPreferencesUseCase
import com.example.zar.features.catchup.domain.usecase.UpdateReminderSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderUiState(
    val isLoading: Boolean = true,
    val settings: ReminderSettings = ReminderSettings(),
    /** When the reminder fires next, skipping a skipped week. Null when the reminder is off. */
    val nextReminder: ZonedDateTime? = null,
    /** True when the reminder that would fire next is the one the person skipped. */
    val isNextSkipped: Boolean = false,
)

@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    observePreferences: ObserveCatchUpPreferencesUseCase,
    private val updateReminder: UpdateReminderSettingsUseCase,
) : ViewModel() {

    val uiState: StateFlow<ReminderUiState> = observePreferences()
        .map { prefs ->
            val now = Instant.now()
            ReminderUiState(
                isLoading = false,
                settings = prefs.reminder,
                nextReminder = ReminderSchedule.next(prefs.reminder, now),
                isNextSkipped = ReminderSchedule.isNextSkipped(prefs.reminder, now),
            )
        }
        .catch { emit(ReminderUiState(isLoading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderUiState())

    fun onEnabledChange(enabled: Boolean) = save { it.copy(enabled = enabled, skippedOccurrence = null) }

    /** Moving the reminder also ends an earlier skip, since the skipped date no longer applies. */
    fun onMove(day: DayOfWeek, time: LocalTime) = save { it.copy(day = day, time = time, skippedOccurrence = null) }

    fun skipThisWeek() = save { ReminderSchedule.skipNext(it, Instant.now()) }

    fun undoSkip() = save { it.copy(skippedOccurrence = null) }

    private fun save(change: (ReminderSettings) -> ReminderSettings) {
        val updated = change(uiState.value.settings)
        // A failed save leaves the screen as it was; the settings are re-read from storage either way.
        viewModelScope.launch { runCatching { updateReminder(updated) } }
    }
}
