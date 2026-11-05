package com.example.zar.features.transactions.domain.activity

import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.DatePreset
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class ActivityMonthsTest {
    private val today = LocalDate.of(2026, 11, 5)

    @Test fun selectingMonthPreservesOtherFiltersAndUsesWholeCalendarMonths() {
        val filter = ActivityFilter(AccountId("bank"), DatePreset.LAST_7_DAYS,
            showVoided = true, query = "coffee", spendingClass = SpendingClass.DISCRETIONARY, categoryKey = "food")
        val february = YearMonth.of(2024, 2)
        val selected = filter.forMonth(february, today)
        assertEquals(filter.copy(preset = DatePreset.CUSTOM, customFrom = february.atDay(1), customTo = february.atEndOfMonth()), selected)
        assertEquals(LocalDate.of(2024, 2, 1)..LocalDate.of(2024, 2, 29), ActivityQuery.rangeOf(selected, today))
        assertEquals(february, selected.wholeMonth(today))
        val current = selected.forMonth(YearMonth.from(today), today)
        assertEquals(DatePreset.THIS_MONTH, current.preset)
        assertNull(current.customFrom)
        assertNull(current.customTo)
        val previous = selected.forMonth(YearMonth.from(today).minusMonths(1), today)
        assertEquals(DatePreset.LAST_MONTH, previous.preset)
    }

    @Test fun arbitraryRangesKeepTheirRangeLabels() {
        assertNull(ActivityFilter(preset = DatePreset.LAST_7_DAYS).wholeMonth(today))
        assertNull(ActivityFilter(preset = DatePreset.CUSTOM, customFrom = today.minusDays(2), customTo = today).wholeMonth(today))
    }
}
