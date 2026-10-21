package com.example.zar.features.transactions.domain.activity

import com.example.zar.core.domain.calculation.PeriodSummary
import java.math.BigDecimal
import java.math.RoundingMode

/** The teal share of Activity's Received/Spent surface, using the same filtered totals as its labels. */
object ActivityBalance {
    /** No proportion is claimed for missing conversions, loading, or a selection with no money. */
    fun receivedFraction(summary: PeriodSummary?): Float? {
        if (summary == null || !summary.received.isComplete || !summary.spent.total.isComplete) return null
        val received = summary.received.amount.toDecimal().max(BigDecimal.ZERO)
        val spent = summary.spent.total.amount.toDecimal().max(BigDecimal.ZERO)
        val total = received + spent
        return if (total.signum() == 0) null else received.divide(total, 8, RoundingMode.HALF_UP).toFloat()
    }
}
