package com.example.zar.features.catchup.data

import com.example.zar.features.auth.domain.repository.SignOutHandler
import com.example.zar.features.catchup.domain.repository.CatchUpPreferencesRepository
import com.example.zar.features.catchup.domain.usecase.ScheduleReminderUseCase
import javax.inject.Inject

/**
 * The last catch-up date belongs to the person who signed out, so it is forgotten with the reminder settings.
 * The default reminder is arranged again for whoever uses the phone next.
 */
class ResetCatchUpOnSignOut @Inject constructor(
    private val preferences: CatchUpPreferencesRepository,
    private val scheduleReminder: ScheduleReminderUseCase,
) : SignOutHandler {
    override suspend fun onSignOut() {
        preferences.clear()
        scheduleReminder()
    }
}
