package com.example.zar.features.catchup.domain

import com.example.zar.features.catchup.domain.model.ReminderSettings
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleTest {
    private val colombo = ZoneId.of("Asia/Colombo")
    private val default = ReminderSettings()

    private fun at(date: String, time: String): Instant = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), colombo).toInstant()

    private fun next(settings: ReminderSettings, now: Instant) = ReminderSchedule.next(settings, now)

    @Test
    fun theDefaultIsSundayAtSevenPm() {
        assertTrue(default.enabled)
        assertEquals(DayOfWeek.SUNDAY, default.day)
        assertEquals(LocalTime.of(19, 0), default.time)
        assertNull(default.skippedOccurrence)
    }

    @Test
    fun fromMidweekTheNextReminderIsTheComingSundayInColombo() {
        // 21 Oct 2026 is a Wednesday.
        val result = next(default, at("2026-10-21", "10:00"))!!

        assertEquals(LocalDate.of(2026, 10, 25), result.toLocalDate())
        assertEquals(DayOfWeek.SUNDAY, result.dayOfWeek)
        assertEquals(LocalTime.of(19, 0), result.toLocalTime())
        assertEquals(colombo, result.zone)
    }

    @Test
    fun onTheReminderDayBeforeTheTimeItFiresToday() {
        val result = next(default, at("2026-10-25", "18:59"))!!

        assertEquals(LocalDate.of(2026, 10, 25), result.toLocalDate())
    }

    @Test
    fun atExactlyTheReminderTimeTheNextOneIsNextWeek() {
        val result = next(default, at("2026-10-25", "19:00"))!!

        assertEquals(LocalDate.of(2026, 11, 1), result.toLocalDate())
    }

    @Test
    fun afterTheReminderTimeItWaitsForNextWeek() {
        val result = next(default, at("2026-10-25", "21:30"))!!

        assertEquals(LocalDate.of(2026, 11, 1), result.toLocalDate())
    }

    @Test
    fun theDayIsDecidedInColomboNotInUtc() {
        // Sunday 23:30 UTC is already Monday 05:00 in Colombo, so this Sunday's reminder has passed.
        val now = Instant.parse("2026-10-25T23:30:00Z")

        assertEquals(LocalDate.of(2026, 11, 1), next(default, now)!!.toLocalDate())
    }

    @Test
    fun aMovedDayAndTimeAreUsed() {
        val moved = default.copy(day = DayOfWeek.FRIDAY, time = LocalTime.of(8, 30))

        val result = next(moved, at("2026-10-21", "10:00"))!!

        assertEquals(LocalDate.of(2026, 10, 23), result.toLocalDate())
        assertEquals(LocalTime.of(8, 30), result.toLocalTime())
    }

    @Test
    fun aReminderThatIsOffHasNoNextTime() {
        assertNull(next(default.copy(enabled = false), at("2026-10-21", "10:00")))
    }

    @Test
    fun skippingTheNextReminderMovesItByAWeek() {
        val now = at("2026-10-21", "10:00")

        val skipped = ReminderSchedule.skipNext(default, now)

        assertEquals(LocalDate.of(2026, 10, 25), skipped.skippedOccurrence)
        assertEquals(LocalDate.of(2026, 11, 1), next(skipped, now)!!.toLocalDate())
        assertTrue(ReminderSchedule.isNextSkipped(skipped, now))
    }

    @Test
    fun onlyOneReminderIsSkippedAndTheSkipEndsOnceItHasPassed() {
        val skipped = ReminderSchedule.skipNext(default, at("2026-10-21", "10:00"))

        // After the skipped Sunday has gone, the following reminders are back to normal.
        val later = at("2026-10-26", "09:00")

        assertEquals(LocalDate.of(2026, 11, 1), next(skipped, later)!!.toLocalDate())
        assertFalse(ReminderSchedule.isNextSkipped(skipped, later))
    }

    @Test
    fun skippingTwiceInARowSkipsTheSameReminderNotTheOneAfter() {
        val now = at("2026-10-21", "10:00")
        val once = ReminderSchedule.skipNext(default, now)

        val twice = ReminderSchedule.skipNext(once, now)

        assertEquals(once.skippedOccurrence, twice.skippedOccurrence)
    }

    @Test
    fun skippingAReminderThatIsOffChangesNothing() {
        val off = default.copy(enabled = false)

        assertEquals(off, ReminderSchedule.skipNext(off, at("2026-10-21", "10:00")))
        assertFalse(ReminderSchedule.isNextSkipped(off, at("2026-10-21", "10:00")))
    }

    @Test
    fun aSkipForADateThatIsAlreadyBehindDoesNotMatter() {
        val stale = default.copy(skippedOccurrence = LocalDate.of(2026, 10, 11))

        assertEquals(LocalDate.of(2026, 10, 25), next(stale, at("2026-10-21", "10:00"))!!.toLocalDate())
        assertFalse(ReminderSchedule.isNextSkipped(stale, at("2026-10-21", "10:00")))
    }
}
