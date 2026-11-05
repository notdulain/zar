package com.example.zar.features.home.domain.model

import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal

/** How far the donut figures can be trusted for the selected month. Overspent wins, then the order below. */
enum class DonutStatus {
    /** Spent and For plans are both known and Received covers them. */
    COMPLETE,

    /** Spent and For plans exceed Received, so there is no honest "still free" share. */
    OVERSPENT,

    /** Some income or spending has no known LKR rate, so the totals are partial. */
    UNRESOLVED_RATES,

    /** Nothing was received in the month, so no share can be drawn. */
    NO_INCOME,

    /** The schema cannot say how much was reserved for plans in this month. */
    PLANS_UNAVAILABLE,
}

/**
 * Where the selected month's Received income went: Spent, For plans and the rest, Still free.
 *
 * [forPlans] is null when no timestamped reservation history exists. Goal allocations carry no date, so they cannot be
 * attributed to a calendar month without guessing; the donut therefore reports For plans as unavailable instead of
 * spreading lifetime allocations over one month. When a dated reservation source arrives, pass it to [from]. [stillFree]
 * is a share of recorded income, not a bank balance, and is signed so overspending stays visible.
 */
data class HomeDonut(
    val received: ReportingTotal,
    val spent: ReportingTotal,
    val forPlans: ReportingTotal?,
    val status: DonutStatus,
) {
    /** Received less Spent less any known reservations. Negative when the month is overspent. */
    val stillFreeMinor: Long
        get() = received.amount.minorUnits - spent.amount.minorUnits - (forPlans?.amount?.minorUnits ?: 0L)

    /**
     * Whether the ring can be drawn from the recorded amounts. Unresolved rates still draw it, because the figures
     * shown beside it are the same recorded amounts and the screen flags the gap; it needs positive income that
     * spending has not passed.
     */
    private val canDraw: Boolean
        get() = when (status) {
            DonutStatus.COMPLETE, DonutStatus.PLANS_UNAVAILABLE -> true
            DonutStatus.UNRESOLVED_RATES -> received.amount.minorUnits > 0L && stillFreeMinor >= 0L
            DonutStatus.OVERSPENT, DonutStatus.NO_INCOME -> false
        }

    /** Whole-number Still free percentage of Received, or null when it would mislead. */
    val stillFreePercent: Int?
        get() = if (canDraw) {
            Math.round(stillFreeMinor.toDouble() / received.amount.minorUnits * 100.0).toInt().coerceIn(0, 100)
        } else {
            null
        }

    /** Ring shares of Received as fractions in 0..1 for Spent, For plans and Still free; null when no ring should be drawn. */
    val arcFractions: List<Float>?
        get() {
            if (!canDraw) return null
            val total = received.amount.minorUnits.toDouble()
            val spentShare = (spent.amount.minorUnits / total).toFloat()
            val plansShare = ((forPlans?.amount?.minorUnits ?: 0L) / total).toFloat()
            return listOf(spentShare, plansShare, (stillFreeMinor / total).toFloat())
        }

    companion object {
        fun from(summary: PeriodSummary, forPlans: ReportingTotal? = null): HomeDonut {
            val received = summary.received
            val spent = summary.spent.total
            val receivedMinor = received.amount.minorUnits
            val committed = spent.amount.minorUnits + (forPlans?.amount?.minorUnits ?: 0L)
            val status = when {
                // Overspent only when Received is fully known. A partial Received total is a lower bound, so Spent above it
                // proves nothing; that case falls through to UNRESOLVED_RATES. Partial Spent is still a lower bound on Spent.
                received.isComplete && committed > maxOf(receivedMinor, 0L) -> DonutStatus.OVERSPENT
                !received.isComplete || !spent.isComplete || forPlans?.isComplete == false -> DonutStatus.UNRESOLVED_RATES
                receivedMinor <= 0L -> DonutStatus.NO_INCOME
                forPlans == null -> DonutStatus.PLANS_UNAVAILABLE
                else -> DonutStatus.COMPLETE
            }
            return HomeDonut(received, spent, forPlans, status)
        }
    }
}
