package com.example.zar.core.domain.model.money

enum class AssetKind { FIAT, CRYPTO }

/**
 * A fiat currency or crypto asset. [fractionDigits] is the smallest unit: 2 for LKR cents,
 * 8 for BTC satoshis.
 */
data class Currency(
    val code: String,
    val kind: AssetKind,
    val fractionDigits: Int,
) {
    init {
        require(code.isNotBlank() && code == code.uppercase()) { "Currency code must be upper case: $code" }
        require(fractionDigits in 0..18) { "Unsupported fraction digits: $fractionDigits" }
    }
}

object Currencies {
    val LKR = Currency("LKR", AssetKind.FIAT, 2)
    val USD = Currency("USD", AssetKind.FIAT, 2)
    val EUR = Currency("EUR", AssetKind.FIAT, 2)
    val GBP = Currency("GBP", AssetKind.FIAT, 2)
    val AUD = Currency("AUD", AssetKind.FIAT, 2)
    val INR = Currency("INR", AssetKind.FIAT, 2)
    val USDT = Currency("USDT", AssetKind.CRYPTO, 6)
    val USDC = Currency("USDC", AssetKind.CRYPTO, 6)
    val BTC = Currency("BTC", AssetKind.CRYPTO, 8)
    val ETH = Currency("ETH", AssetKind.CRYPTO, 18)
    val BNB = Currency("BNB", AssetKind.CRYPTO, 8)

    /** Home totals, goals and allowance are reported in this currency. */
    val REPORTING = LKR

    val all: List<Currency> = listOf(LKR, USD, EUR, GBP, AUD, INR, USDT, USDC, BTC, ETH, BNB)

    fun find(code: String): Currency? = all.firstOrNull { it.code == code.trim().uppercase() }
}
