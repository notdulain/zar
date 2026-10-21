package com.example.zar.features.catchup.domain

import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.features.catchup.domain.model.ReminderSettings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** When the weekly reminder fires. Pure, so the schedule can be checked without an Android clock. */
object ReminderSchedule {

    /**
     * The next reminder strictly after [now], skipping a week the person chose to skip. Null when reminders are
     * off.
     */
    fun next(settings: ReminderSettings, now: Instant, zone: ZoneId = ZarTime.ZONE): ZonedDateTime? {
        if (!settings.enabled) return null
        var date = now.atZone(zone).toLocalDate().with(TemporalAdjusters.nextOrSame(settings.day))
        if (!ZonedDateTime.of(date, settings.time, zone).toInstant().isAfter(now)) date = date.plusWeeks(1)
        val skipped = settings.skippedOccurrence
        if (skipped != null && date <= skipped) date = date.plusWeeks(1)
        return ZonedDateTime.of(date, settings.time, zone)
    }

    /** Settings that skip the reminder that would fire next. Nothing changes when reminders are off. */
    fun skipNext(settings: ReminderSettings, now: Instant, zone: ZoneId = ZarTime.ZONE): ReminderSettings {
        val upcoming = next(settings.copy(skippedOccurrence = null), now, zone) ?: return settings
        return settings.copy(skippedOccurrence = upcoming.toLocalDate())
    }

    /** Whether the reminder that would fire next is the one that was skipped. */
    fun isNextSkipped(settings: ReminderSettings, now: Instant, zone: ZoneId = ZarTime.ZONE): Boolean {
        val skipped = settings.skippedOccurrence ?: return false
        val upcoming = next(settings.copy(skippedOccurrence = null), now, zone) ?: return false
        return upcoming.toLocalDate() <= skipped
    }
}
