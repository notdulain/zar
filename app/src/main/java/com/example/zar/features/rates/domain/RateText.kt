package com.example.zar.features.rates.domain

import java.math.BigDecimal

/** Reads a rate the user typed ("1 USD = ? LKR"). Anything unclear is rejected so a rate is never guessed. */
object RateText {
    /** Groups of exactly three digits after the first group: 1,100 and 12,345,678. */
    private val thousandsGroups = Regex("""^\d{1,3}(,\d{3})+$""")

    /**
     * The rate as a positive number, or null when the text is empty, not a number, or ambiguous.
     *
     * - A dot means commas are thousands separators: 1,100.50 is 1100.50. A comma after the last dot (1.100,50) is
     *   ambiguous, so it is rejected.
     * - Digits grouped in threes are thousands: 1,100 is 1100 (no currency is worth about 1.1 LKR).
     * - One comma before one or two digits is a decimal separator, as typed from the decimal key of a phone set to a
     *   comma-decimal language: 300,5 is 300.5. It used to be dropped, which turned 300,5 into 3005.
     */
    fun parse(text: String): BigDecimal? {
        val typed = text.trim()
        if (typed.isEmpty()) return null
        val normal = when {
            // A comma after the last dot is a decimal comma ("1.100,50"): reading it as thousands would be 1000 times off.
            '.' in typed -> if (',' in typed.substringAfterLast('.')) return null else typed.replace(",", "")
            ',' !in typed -> typed
            thousandsGroups.matches(typed) -> typed.replace(",", "")
            typed.count { it == ',' } == 1 && typed.substringAfter(',').length in 1..2 -> typed.replace(',', '.')
            else -> return null
        }
        return normal.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    }
}
