package com.example.zar.features.catchup.domain.repository

import com.example.zar.features.catchup.domain.model.CatchUpPreferences
import com.example.zar.features.catchup.domain.model.ReminderSettings
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow

/** What catch-up remembers on this phone: the reminder settings and the date of the last finished catch-up. */
interface CatchUpPreferencesRepository {
    fun observe(): Flow<CatchUpPreferences>
    suspend fun saveReminder(settings: ReminderSettings)
    suspend fun markCaughtUp(date: LocalDate)

    /** Forgets everything, for example when someone signs out. */
    suspend fun clear()
}

/** Arranges for the weekly reminder to fire. Scheduling again replaces the earlier reminder. */
interface ReminderScheduler {
    /** Fires once at [at], or cancels any pending reminder when [at] is null. */
    fun schedule(at: ZonedDateTime?)
}
