package com.example.zar.features.bills.domain

import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillFrequency
import java.time.LocalDate
import java.time.YearMonth

/** Pure due-date rules for bills. */
object BillSchedule {

    /**
     * The [index]th due date, always worked out from the first one so a clamped month end
     * doesn't drift: a bill on the 31st is due 28/29 Feb and back on 31 Mar.
     */
    fun dueDate(bill: Bill, index: Int): LocalDate = when (bill.frequency) {
        BillFrequency.WEEKLY -> bill.firstDue.plusWeeks(index.toLong())
        BillFrequency.MONTHLY -> YearMonth.from(bill.firstDue).plusMonths(index.toLong()).let { month ->
            month.atDay(minOf(bill.firstDue.dayOfMonth, month.lengthOfMonth()))
        }
        // plusYears clamps 29 Feb to 28 Feb in other years and returns to 29 Feb in leap years.
        BillFrequency.YEARLY -> bill.firstDue.plusYears(index.toLong())
    }

    /** Due dates in [from, to], oldest first. */
    fun dueDates(bill: Bill, from: LocalDate, to: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var index = 0
        while (true) {
            val date = dueDate(bill, index++)
            if (date > to) break
            if (date >= from) dates += date
        }
        return dates
    }

    /** The first due date on or after [day]. */
    fun nextDueDate(bill: Bill, day: LocalDate): LocalDate {
        var index = 0
        while (true) {
            val date = dueDate(bill, index++)
            if (date >= day) return date
        }
    }

    fun annualCost(bill: Bill): Money = bill.amount.times(
        when (bill.frequency) {
            BillFrequency.WEEKLY -> 52
            BillFrequency.MONTHLY -> 12
            BillFrequency.YEARLY -> 1
        },
    )

    /** Stable ID for a due date, so saving it again (or on another device) updates the same record. */
    fun occurrenceId(bill: Bill, dueDate: LocalDate): ExpectedPaymentId = ExpectedPaymentId("bill-${bill.id.value}-$dueDate")

    /** A due date as an outgoing expected payment. It is never counted in Spent. */
    fun expectedPayment(bill: Bill, dueDate: LocalDate): ExpectedPayment = ExpectedPayment(
        id = occurrenceId(bill, dueDate),
        direction = Direction.OUT,
        amount = bill.amount,
        dueDate = dueDate,
        title = bill.name,
        source = ExpectedSource.BillOccurrence(bill.id.value, dueDate),
        accountId = bill.accountId,
        createdAt = bill.createdAt,
    )

    private fun Money.times(factor: Int): Money = when (this) {
        is FiatMoney -> FiatMoney(Math.multiplyExact(minorUnits, factor.toLong()), currency)
        is CryptoAmount -> CryptoAmount.of(quantity.multiply(factor.toBigDecimal()), currency)
    }
}
