package com.example.zar.features.reminders.domain

import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.bills.domain.model.BillsOverview
import com.example.zar.features.freelance.domain.model.FreelanceOverview
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ReminderKind { BILL_DUE, MILESTONE_OVERDUE }

/** One notification to show. [key] identifies it so it is shown once and never repeated. */
data class DueReminder(
    val key: String,
    val kind: ReminderKind,
    val name: String,
    /** The project, for a milestone. */
    val group: String?,
    val dueDate: LocalDate,
    /** What is still expected (bill) or still to receive (milestone). */
    val amount: Money,
    /** Days from today to the due date; negative when overdue. */
    val daysUntilDue: Int,
)

/** Kept on this phone only. */
data class ReminderPreferences(
    val notifiedKeys: Set<String> = emptySet(),
    val overdueMilestoneAlerts: Boolean = true,
)

/**
 * Decides which reminders are due today. Pure: everything comes from current records, so an
 * edited, stopped, paid or cancelled item simply stops producing a reminder, and a reminder
 * already shown (its key is remembered) is never shown again.
 */
object ReminderPlanner {

    /** Remembered keys older than this are forgotten. */
    const val KEEP_DAYS = 90L

    fun billKey(billId: String, dueDate: LocalDate) = "bill|$billId|$dueDate"

    /** Includes the due date, so a milestone moved to a new date can notify once more for that date. */
    fun milestoneKey(milestoneId: String, dueDate: LocalDate) = "milestone|$milestoneId|$dueDate"

    fun plan(
        bills: BillsOverview,
        freelance: FreelanceOverview,
        preferences: ReminderPreferences,
        today: LocalDate,
    ): List<DueReminder> {
        val billReminders = bills.bills
            .filter { it.bill.runsOn(today) }
            .mapNotNull { summary ->
                val reminder = summary.bill.reminder ?: return@mapNotNull null
                val next = summary.next ?: return@mapNotNull null
                val due = next.dueDate
                // From the reminder day up to the due date, so a day missed by Android still catches up.
                if (today < due.minusDays(reminder.daysBefore.toLong()) || today > due) return@mapNotNull null
                if (next.progress.state == MatchState.MATCHED || next.progress.state == MatchState.CANCELLED) return@mapNotNull null
                DueReminder(
                    key = billKey(summary.bill.id.value, due),
                    kind = ReminderKind.BILL_DUE,
                    name = summary.bill.name,
                    group = null,
                    dueDate = due,
                    amount = next.progress.remaining,
                    daysUntilDue = ChronoUnit.DAYS.between(today, due).toInt(),
                )
            }

        val milestoneReminders = if (!preferences.overdueMilestoneAlerts) {
            emptyList()
        } else {
            freelance.projects
                .filterNot { it.project.isArchived }
                .flatMap { project ->
                    project.milestones
                        .filter { it.progress.state != MatchState.CANCELLED && it.progress.overdue.isPositive }
                        // Keys this old are pruned, so alerting on them would repeat every day.
                        .filterNot { it.milestone.dueDate.isBefore(today.minusDays(KEEP_DAYS)) }
                        .map { milestone ->
                            val due = milestone.milestone.dueDate
                            DueReminder(
                                key = milestoneKey(milestone.milestone.id.value, due),
                                kind = ReminderKind.MILESTONE_OVERDUE,
                                name = milestone.milestone.title,
                                group = project.project.name,
                                dueDate = due,
                                amount = milestone.progress.overdue,
                                daysUntilDue = ChronoUnit.DAYS.between(today, due).toInt(),
                            )
                        }
                }
        }

        return (billReminders + milestoneReminders).filterNot { it.key in preferences.notifiedKeys }
    }

    /** Drops remembered keys whose date is long past, so the set stays small. */
    fun prune(keys: Set<String>, today: LocalDate): Set<String> = keys.filterTo(mutableSetOf()) { key ->
        val date = key.substringAfterLast('|').let { runCatching { LocalDate.parse(it) }.getOrNull() }
        date == null || !date.isBefore(today.minusDays(KEEP_DAYS))
    }
}
