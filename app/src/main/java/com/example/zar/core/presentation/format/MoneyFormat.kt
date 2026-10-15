package com.example.zar.core.presentation.format

import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Money
import java.text.NumberFormat
import java.util.Locale

/** "1,250" for whole amounts and "1,250.50" otherwise. Crypto keeps up to 8 decimals. */
fun Money.formatNumber(): String {
    val value = toDecimal()
    val maxDigits = currency.fractionDigits.coerceAtMost(MAX_CRYPTO_DIGITS)
    val hasFraction = value.stripTrailingZeros().scale() > 0
    val format = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = maxDigits
        minimumFractionDigits = if (hasFraction) maxDigits.coerceAtMost(FIAT_DIGITS) else 0
    }
    return format.format(value)
}

/** "LKR 148,350", always with the currency code so no amount is ambiguous. */
fun Money.formatWithCode(): String = "${currency.code} ${formatNumber()}"

/** Rows inside a list: LKR amounts are shown bare, other currencies keep their code. */
fun Money.formatForRow(): String =
    if (currency == Currencies.LKR) formatNumber() else formatWithCode()

private const val FIAT_DIGITS = 2
private const val MAX_CRYPTO_DIGITS = 8
