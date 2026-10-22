package com.example.zar.features.reminders.domain

import com.example.zar.features.bills.domain.usecase.ObserveBillsUseCase
import com.example.zar.features.freelance.domain.usecase.ObserveFreelanceUseCase
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Reminder bookkeeping on this phone. */
interface ReminderPreferencesRepository {
    fun observe(): Flow<ReminderPreferences>

    /** Remembers that these reminders were shown, and forgets very old ones. */
    suspend fun markNotified(keys: Set<String>, today: LocalDate)

    suspend fun setOverdueMilestoneAlerts(enabled: Boolean)

    suspend fun clear()
}

/** Runs the daily reminder check in the background. Scheduling twice never creates two jobs. */
interface PaymentReminderScheduler {
    /** Makes sure the daily check exists. */
    fun ensureScheduled()

    /** Runs a check soon, e.g. right after a reminder setting changes. */
    fun checkNow()

    fun cancel()
}

/** The reminders to show today that haven't been shown before. */
class PlanRemindersUseCase @Inject constructor(
    private val observeBills: ObserveBillsUseCase,
    private val observeFreelance: ObserveFreelanceUseCase,
    private val preferences: ReminderPreferencesRepository,
) {
    suspend operator fun invoke(today: LocalDate): List<DueReminder> =
        ReminderPlanner.plan(observeBills(today).first(), observeFreelance(today).first(), preferences.observe().first(), today)
}

class MarkRemindersShownUseCase @Inject constructor(
    private val preferences: ReminderPreferencesRepository,
) {
    suspend operator fun invoke(reminders: List<DueReminder>, today: LocalDate) {
        if (reminders.isNotEmpty()) preferences.markNotified(reminders.map { it.key }.toSet(), today)
    }
}

class ObserveOverdueMilestoneAlertsUseCase @Inject constructor(
    private val preferences: ReminderPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = preferences.observe().map { it.overdueMilestoneAlerts }
}

class SetOverdueMilestoneAlertsUseCase @Inject constructor(
    private val preferences: ReminderPreferencesRepository,
    private val scheduler: PaymentReminderScheduler,
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferences.setOverdueMilestoneAlerts(enabled)
        if (enabled) scheduler.checkNow()
    }
}

/** Called after a bill's reminder changes, so a reminder due today isn't missed. */
class RefreshRemindersUseCase @Inject constructor(
    private val scheduler: PaymentReminderScheduler,
) {
    operator fun invoke() {
        scheduler.ensureScheduled()
        scheduler.checkNow()
    }
}
