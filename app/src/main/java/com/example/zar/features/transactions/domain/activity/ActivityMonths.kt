package com.example.zar.features.transactions.domain.activity

import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.DatePreset
import java.time.LocalDate
import java.time.YearMonth

/** Changes only the date selection; account, category, search and visibility choices stay applied. */
fun ActivityFilter.forMonth(month: YearMonth, today: LocalDate): ActivityFilter {
    val current = YearMonth.from(today)
    return when (month) {
        current -> copy(preset = DatePreset.THIS_MONTH, customFrom = null, customTo = null)
        current.minusMonths(1) -> copy(preset = DatePreset.LAST_MONTH, customFrom = null, customTo = null)
        else -> copy(preset = DatePreset.CUSTOM, customFrom = month.atDay(1), customTo = month.atEndOfMonth())
    }
}

/** A complete calendar month, including months selected through the custom-date filters. */
fun ActivityFilter.wholeMonth(today: LocalDate): YearMonth? {
    val range = ActivityQuery.rangeOf(this, today)
    val month = YearMonth.from(range.start)
    return month.takeIf { range.start == it.atDay(1) && range.endInclusive == it.atEndOfMonth() }
}
