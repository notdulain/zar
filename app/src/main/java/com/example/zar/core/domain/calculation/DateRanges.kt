package com.example.zar.core.domain.calculation

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object ZarTime {
    /** All dates (months, allowance weeks, reminders) are calendar days in Sri Lanka. */
    val ZONE: ZoneId = ZoneId.of("Asia/Colombo")

    fun today(): LocalDate = LocalDate.now(ZONE)
}

fun YearMonth.toDateRange(): ClosedRange<LocalDate> = atDay(1)..atEndOfMonth()

/** The week containing [date], starting on [weekStart] (Monday by default). */
fun weekContaining(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): ClosedRange<LocalDate> {
    val start = date.with(TemporalAdjusters.previousOrSame(weekStart))
    return start..start.plusDays(6)
}
