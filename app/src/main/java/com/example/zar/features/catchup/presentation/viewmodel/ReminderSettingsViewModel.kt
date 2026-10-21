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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderUiState(
    val isLoading: Boolean = true,
    val saveFailed: Boolean = false,
    val settings: ReminderSettings = ReminderSettings(),
    /** When the reminder fires next, skipping a skipped week. Null when the reminder is off. */
    val nextReminder: ZonedDateTime? = null,
    /** True when the reminder that would fire next is the one the person skipped. */
    val isNextSkipped: Boolean = false,
)

@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    private val observePreferences: ObserveCatchUpPreferencesUseCase,
    private val updateReminder: UpdateReminderSettingsUseCase,
) : ViewModel() {

    private val saveFailed = MutableStateFlow(false)
    private val saveMutex = Mutex()
    val uiState: StateFlow<ReminderUiState> = combine(observePreferences(), saveFailed) { prefs, failed ->
            val now = Instant.now()
            ReminderUiState(
                isLoading = false,
                saveFailed = failed,
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
        viewModelScope.launch {
            // Apply rapid changes in order to the latest persisted settings, even before UI collection catches up.
            saveMutex.withLock {
                saveFailed.value = false
                runCatching { updateReminder(change(observePreferences().first().reminder)) }
                    .onFailure { saveFailed.value = true }
            }
        }
    }
}
