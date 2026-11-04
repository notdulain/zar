package com.example.zar.features.home.domain.model

import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import java.time.LocalDate
import java.time.YearMonth

/**
 * One calendar day of the cumulative Received bar chart.
 *
 * [cumulative] is the running Received total from day 1 of the month up to and including [date]. It is
 * always zero for a future slot, which is a placeholder and never a forecast.
 */
data class CumulativeIncomeSlot(
    val date: LocalDate,
    val cumulative: ReportingTotal,
    val isFuture: Boolean,
) {
    /** True when nothing has been received yet by this elapsed day, so the chart shows a baseline marker. */
    val isZeroMarker: Boolean get() = !isFuture && cumulative.amount.isZero
}

/**
 * Cumulative Received for one calendar month, one slot per day (28 to 31) and restarting at zero each
 * month. Unresolved-rate income stays counted in [CumulativeIncomeSlot.cumulative] as unresolved, never as zero.
 */
data class CumulativeIncomeSeries(val month: YearMonth, val slots: List<CumulativeIncomeSlot>) {
    /** Largest cumulative total, which is the final elapsed slot because Received never decreases within a month. */
    val maximum: Long get() = slots.maxOfOrNull { it.cumulative.amount.minorUnits } ?: 0L

    val isAllZero: Boolean get() = maximum == 0L

    val hasUnresolved: Boolean get() = slots.any { !it.cumulative.isComplete }

    /** Bar height as a 0..1 fraction of the tallest bar. Zero for an all-zero month, so there is no division by zero. */
    fun fractionOf(slot: CumulativeIncomeSlot): Float {
        val max = maximum
        return if (max <= 0L) 0f else (slot.cumulative.amount.minorUnits.toDouble() / max).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        /**
         * Builds the series from [receivedByDay], the per-day Received totals starting on day 1. [today] marks which
         * slots are still ahead; any day after it is a future placeholder. Missing days count as zero income.
         */
        fun from(month: YearMonth, receivedByDay: List<ReportingTotal>, today: LocalDate): CumulativeIncomeSeries {
            val zero = ReportingTotal.zero(Currencies.REPORTING)
            var running = zero
            val slots = (1..month.lengthOfMonth()).map { day ->
                val date = month.atDay(day)
                val isFuture = date.isAfter(today)
                if (!isFuture) receivedByDay.getOrNull(day - 1)?.let { running = running.plusSaturating(it) }
                CumulativeIncomeSlot(date, if (isFuture) zero else running, isFuture)
            }
            return CumulativeIncomeSeries(month, slots)
        }

        private fun ReportingTotal.plusSaturating(other: ReportingTotal): ReportingTotal {
            val sum = amount.minorUnits.toBigInteger() + other.amount.minorUnits.toBigInteger()
            val clamped = sum.max(Long.MIN_VALUE.toBigInteger()).min(Long.MAX_VALUE.toBigInteger()).toLong()
            return ReportingTotal(FiatMoney(clamped, amount.currency), unresolvedCount + other.unresolvedCount)
        }
    }
}
