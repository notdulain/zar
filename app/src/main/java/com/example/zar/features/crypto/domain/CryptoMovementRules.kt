package com.example.zar.features.crypto.domain

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.AssetKind
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ExchangeRate
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.RateSource
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.crypto.domain.model.CryptoMovement
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

enum class CryptoProblem {
    FROM_MISSING,
    TO_MISSING,
    SAME_ACCOUNT,
    /** Neither account is a crypto wallet; use a normal transfer instead. */
    NO_CRYPTO_ACCOUNT,
    SENT_MISSING,
    SENT_INVALID,
    RECEIVED_MISSING,
    RECEIVED_INVALID,
    VALUE_INVALID,
    COST_INVALID,
    DATE_IN_FUTURE,
    NOTE_TOO_LONG,
}

data class CryptoMovementInput(
    val from: Account?,
    val to: Account?,
    val sent: String,
    /** Blank for a move between wallets of the same asset means "same as sent". */
    val received: String,
    val lkrValue: String,
    val date: LocalDate,
    val note: String,
)

sealed interface CryptoCheck<out T> {
    data class Valid<T>(val value: T) : CryptoCheck<T>

    data class Invalid(val problems: Set<CryptoProblem>) : CryptoCheck<Nothing>
}

/** Pure rules for recording crypto movements as transfers with historical LKR values. */
object CryptoMovementRules {
    const val MAX_NOTE = 500
    private const val RATE_SCALE = 12

    fun kindOf(from: Account, to: Account): CryptoMovementKind? {
        val fromCrypto = from.currency.kind == AssetKind.CRYPTO
        val toCrypto = to.currency.kind == AssetKind.CRYPTO
        return when {
            !fromCrypto && toCrypto -> CryptoMovementKind.BUY
            fromCrypto && !toCrypto -> CryptoMovementKind.SELL
            fromCrypto && toCrypto && from.currency == to.currency -> CryptoMovementKind.MOVE
            fromCrypto && toCrypto -> CryptoMovementKind.TRADE
            else -> null
        }
    }

    /** True when one side is LKR, so the LKR value is that amount and needn't be typed. */
    fun valueIsImplied(from: Account?, to: Account?): Boolean =
        from?.currency == Currencies.REPORTING || to?.currency == Currencies.REPORTING

    fun validate(input: CryptoMovementInput, today: LocalDate): CryptoCheck<CryptoMovement> {
        val problems = mutableSetOf<CryptoProblem>()
        val from = input.from
        val to = input.to
        if (from == null) problems += CryptoProblem.FROM_MISSING
        if (to == null) problems += CryptoProblem.TO_MISSING
        val kind = if (from != null && to != null) {
            if (from.id == to.id) {
                problems += CryptoProblem.SAME_ACCOUNT
                null
            } else {
                kindOf(from, to) ?: null.also { problems += CryptoProblem.NO_CRYPTO_ACCOUNT }
            }
        } else {
            null
        }

        val sent = from?.let { parseAmount(input.sent, it, problems, CryptoProblem.SENT_MISSING, CryptoProblem.SENT_INVALID) }
        val received = when {
            to == null -> null
            kind == CryptoMovementKind.MOVE && input.received.isBlank() -> sent
            else -> parseAmount(input.received, to, problems, CryptoProblem.RECEIVED_MISSING, CryptoProblem.RECEIVED_INVALID)
        }

        val lkrValue: FiatMoney? = when {
            sent?.currency == Currencies.REPORTING -> sent as FiatMoney
            received?.currency == Currencies.REPORTING -> received as FiatMoney
            input.lkrValue.isBlank() -> null
            else -> (FiatMoney.parse(input.lkrValue, Currencies.REPORTING)?.takeIf { it.isPositive })
                ?: null.also { problems += CryptoProblem.VALUE_INVALID }
        }

        if (input.date.isAfter(today)) problems += CryptoProblem.DATE_IN_FUTURE
        val note = input.note.trim()
        if (note.length > MAX_NOTE) problems += CryptoProblem.NOTE_TOO_LONG

        if (problems.isNotEmpty() || from == null || to == null || kind == null || sent == null || received == null) {
            return CryptoCheck.Invalid(problems)
        }
        return CryptoCheck.Valid(CryptoMovement(kind, from, to, sent, received, lkrValue, input.date, note.ifEmpty { null }))
    }

    /** An opening cost in LKR; blank clears it. */
    fun parseOpeningCost(text: String): CryptoCheck<FiatMoney?> {
        if (text.isBlank()) return CryptoCheck.Valid(null)
        val cost = FiatMoney.parse(text, Currencies.REPORTING)?.takeIf { it.isPositive }
            ?: return CryptoCheck.Invalid(setOf(CryptoProblem.COST_INVALID))
        return CryptoCheck.Valid(cost)
    }

    /**
     * Both legs carry the movement's LKR value, fixed at save time: an LKR leg converts to
     * itself; any other leg stores the implied rate. Without a value both stay missing.
     */
    fun newTransfer(id: TransferId, movement: CryptoMovement, createdAt: Instant): Transfer {
        val value = movement.lkrValue
            ?: (movement.sent as? FiatMoney)?.takeIf { it.currency == Currencies.REPORTING }
            ?: (movement.received as? FiatMoney)?.takeIf { it.currency == Currencies.REPORTING }
        return Transfer(
            id = id,
            fromAccountId = movement.from.id,
            toAccountId = movement.to.id,
            sent = movement.sent,
            received = movement.received,
            date = movement.date,
            sentConversion = conversionFor(movement.sent, value, createdAt),
            receivedConversion = conversionFor(movement.received, value, createdAt),
            note = movement.note,
            createdAt = createdAt,
        )
    }

    fun conversionFor(amount: Money, lkrValue: FiatMoney?, asOf: Instant): ReportingConversion {
        if (amount.currency == Currencies.REPORTING) return ReportingConversion.SameCurrency
        val value = lkrValue ?: return ReportingConversion.Missing(Currencies.REPORTING)
        val rate = value.toDecimal().divide(amount.toDecimal(), RATE_SCALE, RoundingMode.HALF_EVEN).stripTrailingZeros()
        if (rate.signum() <= 0) return ReportingConversion.Missing(Currencies.REPORTING)
        return ReportingConversion.Converted(
            rate = ExchangeRate(amount.currency, Currencies.REPORTING, rate, asOf, RateSource.MANUAL),
            amount = value,
        )
    }

    private fun parseAmount(
        text: String,
        account: Account,
        problems: MutableSet<CryptoProblem>,
        missing: CryptoProblem,
        invalid: CryptoProblem,
    ): Money? {
        if (text.isBlank()) {
            problems += missing
            return null
        }
        return Money.parse(text, account.currency)?.takeIf { it.isPositive } ?: null.also { problems += invalid }
    }
}
