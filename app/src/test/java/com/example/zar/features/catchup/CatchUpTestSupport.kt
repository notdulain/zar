package com.example.zar.features.catchup

import com.example.zar.features.catchup.domain.model.CatchUpPreferences
import com.example.zar.features.catchup.domain.model.ReminderSettings
import com.example.zar.features.catchup.domain.repository.CatchUpPreferencesRepository
import com.example.zar.features.catchup.domain.repository.ReminderScheduler
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Preferences kept in memory, with the same shape as the DataStore-backed repository. */
class FakeCatchUpPreferences(initial: CatchUpPreferences = CatchUpPreferences()) : CatchUpPreferencesRepository {
    private val state = MutableStateFlow(initial)
    var saveError: Throwable? = null
    var markError: Throwable? = null

    val current: CatchUpPreferences get() = state.value

    override fun observe(): Flow<CatchUpPreferences> = state

    override suspend fun saveReminder(settings: ReminderSettings) {
        saveError?.let { throw it }
        state.value = state.value.copy(reminder = settings)
    }

    override suspend fun markCaughtUp(date: LocalDate) {
        markError?.let { throw it }
        state.value = state.value.copy(lastCatchUp = date)
    }

    override suspend fun clear() {
        state.value = CatchUpPreferences()
    }
}

/** Remembers every reminder that was arranged. A null entry means the reminder was cancelled. */
class FakeReminderScheduler : ReminderScheduler {
    val scheduled = mutableListOf<ZonedDateTime?>()
    val last: ZonedDateTime? get() = scheduled.lastOrNull()

    override fun schedule(at: ZonedDateTime?) {
        scheduled += at
    }
}
