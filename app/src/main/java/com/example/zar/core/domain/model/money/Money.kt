package com.example.zar.core.domain.model.money

import java.math.BigDecimal

/**
 * An exact amount. Fiat uses integer minor units ([FiatMoney]); crypto uses precise decimals
 * ([CryptoAmount]). Never use Double for money.
 */
sealed interface Money : Comparable<Money> {
    val currency: Currency
    val signum: Int

    /** Exact value in major units, e.g. 1250.50 for LKR 1,250.50. */
    fun toDecimal(): BigDecimal

    operator fun plus(other: Money): Money
    operator fun minus(other: Money): Money

    val isZero: Boolean get() = signum == 0
    val isPositive: Boolean get() = signum > 0
    val isNegative: Boolean get() = signum < 0

    companion object {
        fun zero(currency: Currency): Money = when (currency.kind) {
            AssetKind.FIAT -> FiatMoney.zero(currency)
            AssetKind.CRYPTO -> CryptoAmount.zero(currency)
        }

        /** Parses user input such as "1,250.50". Returns null if invalid or too precise. */
        fun parse(text: String, currency: Currency): Money? = when (currency.kind) {
            AssetKind.FIAT -> FiatMoney.parse(text, currency)
            AssetKind.CRYPTO -> CryptoAmount.parse(text, currency)
        }
    }
}

fun Money.coerceAtLeastZero(): Money = if (isNegative) Money.zero(currency) else this

data class FiatMoney(val minorUnits: Long, override val currency: Currency) : Money {
    init {
        require(currency.kind == AssetKind.FIAT) { "${currency.code} is not a fiat currency" }
    }

    override val signum: Int get() = minorUnits.compareTo(0L)

    override fun toDecimal(): BigDecimal = BigDecimal.valueOf(minorUnits, currency.fractionDigits)

    override fun plus(other: Money): FiatMoney = plus(requireFiat(other))
    override fun minus(other: Money): FiatMoney = minus(requireFiat(other))

    operator fun plus(other: FiatMoney): FiatMoney =
        copy(minorUnits = Math.addExact(minorUnits, requireFiat(other).minorUnits))

    operator fun minus(other: FiatMoney): FiatMoney =
        copy(minorUnits = Math.subtractExact(minorUnits, requireFiat(other).minorUnits))

    override fun compareTo(other: Money): Int = minorUnits.compareTo(requireFiat(other).minorUnits)

    override fun toString(): String = "${currency.code} ${toDecimal().toPlainString()}"

    private fun requireFiat(other: Money): FiatMoney {
        require(other is FiatMoney && other.currency == currency) {
            "Cannot combine ${currency.code} with ${other.currency.code}"
        }
        return other
    }

    companion object {
        fun zero(currency: Currency) = FiatMoney(0, currency)

        /** Exact conversion from major units. Returns null when [amount] has too many decimals. */
        fun ofMajor(amount: BigDecimal, currency: Currency): FiatMoney? = try {
            FiatMoney(amount.movePointRight(currency.fractionDigits).longValueExact(), currency)
        } catch (_: ArithmeticException) {
            null
        }

        fun parse(text: String, currency: Currency): FiatMoney? =
            text.replace(",", "").trim().toBigDecimalOrNull()?.let { ofMajor(it, currency) }
    }
}

/** A crypto quantity with exact decimal precision, limited to the asset's fraction digits. */
class CryptoAmount private constructor(
    val quantity: BigDecimal,
    override val currency: Currency,
) : Money {

    override val signum: Int get() = quantity.signum()

    override fun toDecimal(): BigDecimal = quantity

    override fun plus(other: Money): CryptoAmount = of(quantity + requireCrypto(other).quantity, currency)
    override fun minus(other: Money): CryptoAmount = of(quantity - requireCrypto(other).quantity, currency)

    override fun compareTo(other: Money): Int = quantity.compareTo(requireCrypto(other).quantity)

    override fun equals(other: Any?): Boolean =
        other is CryptoAmount && other.currency == currency && other.quantity.compareTo(quantity) == 0

    override fun hashCode(): Int = 31 * currency.hashCode() + quantity.hashCode()

    override fun toString(): String = "${currency.code} ${quantity.toPlainString()}"

    private fun requireCrypto(other: Money): CryptoAmount {
        require(other is CryptoAmount && other.currency == currency) {
            "Cannot combine ${currency.code} with ${other.currency.code}"
        }
        return other
    }

    companion object {
        fun of(quantity: BigDecimal, currency: Currency): CryptoAmount {
            require(currency.kind == AssetKind.CRYPTO) { "${currency.code} is not a crypto asset" }
            val normalized = if (quantity.signum() == 0) BigDecimal.ZERO else quantity.stripTrailingZeros()
            require(normalized.scale() <= currency.fractionDigits) {
                "${currency.code} supports at most ${currency.fractionDigits} decimals"
            }
            return CryptoAmount(normalized, currency)
        }

        fun zero(currency: Currency) = of(BigDecimal.ZERO, currency)

        fun parse(text: String, currency: Currency): CryptoAmount? =
            text.replace(",", "").trim().toBigDecimalOrNull()?.let { runCatching { of(it, currency) }.getOrNull() }
    }
}
