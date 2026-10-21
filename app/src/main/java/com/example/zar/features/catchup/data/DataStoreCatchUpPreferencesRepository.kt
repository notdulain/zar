package com.example.zar.features.catchup.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.zar.features.catchup.domain.model.CatchUpPreferences
import com.example.zar.features.catchup.domain.model.ReminderSettings
import com.example.zar.features.catchup.domain.repository.CatchUpPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.catchUpStore by preferencesDataStore(name = "catch_up")

/**
 * Keeps the reminder settings and the last catch-up date on this phone. They are small and personal to the
 * device, so they don't need to sync. A missing or unreadable value falls back to the default (Sunday, 7 pm).
 */
@Singleton
class DataStoreCatchUpPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : CatchUpPreferencesRepository {

    override fun observe(): Flow<CatchUpPreferences> = context.catchUpStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val default = ReminderSettings()
            CatchUpPreferences(
                reminder = ReminderSettings(
                    enabled = prefs[ENABLED] ?: default.enabled,
                    day = prefs[DAY]?.let { runCatching { DayOfWeek.of(it) }.getOrNull() } ?: default.day,
                    time = prefs[MINUTE_OF_DAY]?.let { runCatching { LocalTime.ofSecondOfDay(it * 60L) }.getOrNull() } ?: default.time,
                    skippedOccurrence = prefs[SKIPPED]?.let { LocalDate.ofEpochDay(it) },
                ),
                lastCatchUp = prefs[LAST_CATCH_UP]?.let { LocalDate.ofEpochDay(it) },
            )
        }

    override suspend fun saveReminder(settings: ReminderSettings) {
        context.catchUpStore.edit { prefs ->
            prefs[ENABLED] = settings.enabled
            prefs[DAY] = settings.day.value
            prefs[MINUTE_OF_DAY] = settings.time.hour * 60 + settings.time.minute
            val skipped = settings.skippedOccurrence
            if (skipped == null) prefs.remove(SKIPPED) else prefs[SKIPPED] = skipped.toEpochDay()
        }
    }

    override suspend fun markCaughtUp(date: LocalDate) {
        context.catchUpStore.edit { it[LAST_CATCH_UP] = date.toEpochDay() }
    }

    override suspend fun clear() {
        context.catchUpStore.edit { it.clear() }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("reminder_enabled")
        val DAY = intPreferencesKey("reminder_day")
        val MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
        val SKIPPED = longPreferencesKey("reminder_skipped_epoch_day")
        val LAST_CATCH_UP = longPreferencesKey("last_catch_up_epoch_day")
    }
}
