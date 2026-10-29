package com.example.zar.features.transactions.presentation.income

import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.presentation.privacy.AmountMask
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Money
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display formatting for income amounts and dates, matching the wireframes ("+ 125,000", "Oct 24"). */
object IncomeFormat {
    private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val FULL_DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    /** "125,000" for whole amounts, "1,094.95" otherwise. */
    fun amount(money: Money): String {
        if (AmountMask.isHidden) return AmountMask.HIDDEN_TEXT
        val value = money.toDecimal()
        val format = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
        if (value.stripTrailingZeros().scale() > 0) {
            val digits = money.currency.fractionDigits
            format.minimumFractionDigits = minOf(2, digits)
            format.maximumFractionDigits = digits
        }
        return format.format(value)
    }

    /** "+ 125,000" for LKR; foreign income keeps its currency: "+ USD 400". */
    fun received(money: Money): String =
        if (money.currency == Currencies.REPORTING) "+ ${amount(money)}" else "+ ${money.currency.code} ${amount(money)}"

    fun withCode(money: Money): String = "${money.currency.code} ${amount(money)}"

    fun shortDate(date: LocalDate): String = date.format(SHORT_DATE)

    fun fullDate(date: LocalDate): String = date.format(FULL_DATE)

    /** Figma's date value: "Today, 15 Oct", "Yesterday, 14 Oct", otherwise "Tue, 13 Oct". */
    fun dateLabel(date: LocalDate, today: LocalDate = ZarTime.today()): String {
        val day = date.format(DAY_MONTH)
        return when (date) {
            today -> "Today, $day"
            today.minusDays(1) -> "Yesterday, $day"
            else -> "${date.format(WEEKDAY)}, $day"
        }
    }

    fun month(month: YearMonth): String = month.format(MONTH).uppercase(Locale.ENGLISH)
}
