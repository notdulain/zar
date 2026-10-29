package com.example.zar.features.transactions.presentation.income

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class IncomeFormatDateLabelTest {
    private val today = LocalDate.of(2026, 10, 15)

    @Test fun todayIsNamedAndCarriesTheDayAndMonth() {
        assertEquals("Today, 15 Oct", IncomeFormat.dateLabel(today, today))
    }

    @Test fun yesterdayIsNamedToo() {
        assertEquals("Yesterday, 14 Oct", IncomeFormat.dateLabel(today.minusDays(1), today))
    }

    @Test fun olderDatesShowTheWeekday() {
        assertEquals("Tue, 13 Oct", IncomeFormat.dateLabel(LocalDate.of(2026, 10, 13), today))
    }

    @Test fun aDateEarlierInTheYearShowsItsWeekday() {
        assertEquals("Sun, 1 Feb", IncomeFormat.dateLabel(LocalDate.of(2026, 2, 1), today))
    }
}
