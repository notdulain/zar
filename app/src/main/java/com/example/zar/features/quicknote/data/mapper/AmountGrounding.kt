package com.example.zar.features.quicknote.data.mapper

import java.math.BigDecimal
import java.text.Normalizer

/**
 * Checks that an amount the model returned is actually written in the note. Digits in any script count
 * (Sinhala, Tamil, Arabic-Indic, full-width), and a trailing magnitude is applied: "2.5k", "1.5 lakh", "2mn".
 * An amount with no matching number is not rejected, only flagged for review.
 */
internal object AmountGrounding {

    private val MAGNITUDES: Map<String, BigDecimal> = mapOf(
        "k" to BigDecimal(1_000), "thousand" to BigDecimal(1_000),
        "lakh" to BigDecimal(100_000), "lakhs" to BigDecimal(100_000), "lac" to BigDecimal(100_000),
        "lacs" to BigDecimal(100_000), "lk" to BigDecimal(100_000),
        "mn" to BigDecimal(1_000_000), "m" to BigDecimal(1_000_000), "million" to BigDecimal(1_000_000),
        "cr" to BigDecimal(10_000_000), "crore" to BigDecimal(10_000_000),
    )

    private val NUMBER = Regex("""(\d[\d,]*(?:\.\d+)?)\s{0,2}([a-z]{1,8})?""")

    fun isGrounded(amount: BigDecimal, note: String): Boolean =
        numbersIn(note).any { it.compareTo(amount) == 0 }

    /** Every number in [note] with its magnitude applied. A word that is not a magnitude is not part of the number. */
    fun numbersIn(note: String): List<BigDecimal> {
        val text = normalizeDigits(note).lowercase()
        return NUMBER.findAll(text).mapNotNull { match ->
            val digits = match.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return@mapNotNull null
            val word = match.groupValues[2]
            val factor = MAGNITUDES[word]
            // "2 lakh" scales; "2 days" or "2k" (k matched) are separated correctly; a bare number keeps its value.
            if (factor != null) digits.multiply(factor) else digits
        }.toList()
    }

    private fun normalizeDigits(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val out = StringBuilder(folded.length)
        for (ch in folded) {
            val digit = if (Character.isDigit(ch)) Character.digit(ch, 10) else -1
            if (digit >= 0) out.append(('0' + digit)) else out.append(ch)
        }
        return out.toString()
    }
}
