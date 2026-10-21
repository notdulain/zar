package com.example.zar.features.crypto.domain

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.inLegId
import com.example.zar.core.domain.model.money.AssetKind
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.outLegId
import com.example.zar.features.crypto.domain.model.AssetPosition
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import com.example.zar.features.crypto.domain.model.CryptoMovementRow
import com.example.zar.features.crypto.domain.model.CryptoPortfolio
import com.example.zar.features.crypto.domain.model.OpeningCost
import com.example.zar.features.crypto.domain.model.RealizedResult
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

/**
 * Average-cost holdings and realized profit or loss per crypto asset, worked out from saved
 * records only. Results are never saved and never reach Received or Spent; a missing cost or
 * value makes a result unknown instead of guessed. Undone or corrected records apply
 * automatically because everything is recomputed from the current ledger.
 */
object CryptoPortfolioCalculator {

    private val MATH = MathContext.DECIMAL128
    private val LKR = Currencies.REPORTING

    fun calculate(
        accounts: List<Account>,
        entries: List<LedgerEntry>,
        openingCosts: Map<AccountId, OpeningCost>,
    ): CryptoPortfolio {
        val wallets = accounts.filter { it.type == AccountType.CRYPTO && it.currency.kind == AssetKind.CRYPTO }
        val walletsById = wallets.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        val active = entries.active()
        val byId = active.associateBy { it.id }

        // Same-asset moves between owned wallets carry cost across, so their legs are skipped; only a
        // difference between what left and what arrived (a network fee) changes holdings.
        fun internalMoveIn(transferId: TransferId?): Transaction? {
            transferId ?: return null
            val out = byId[transferId.outLegId()]?.current ?: return null
            val inLeg = byId[transferId.inLegId()]?.current ?: return null
            val internal = out.accountId in walletsById && inLeg.accountId in walletsById && out.amount.currency == inLeg.amount.currency
            return inLeg.takeIf { internal }
        }

        val results = mutableMapOf<TransactionId, RealizedResult>()
        val assets = wallets.groupBy { it.currency }.map { (currency, assetWallets) ->
            calculateAsset(currency, assetWallets, active, openingCosts, ::internalMoveIn, results)
        }.sortedBy { it.currency.code }

        val movements = active
            .filter { it.current.type == TransactionType.TRANSFER_OUT }
            .mapNotNull { out ->
                val transferId = out.current.transferId ?: return@mapNotNull null
                val inEntry = byId[transferId.inLegId()] ?: return@mapNotNull null
                val from = accountsById[out.current.accountId] ?: return@mapNotNull null
                val to = accountsById[inEntry.current.accountId] ?: return@mapNotNull null
                if (from.id !in walletsById && to.id !in walletsById) return@mapNotNull null
                val kind = CryptoMovementRules.kindOf(from, to) ?: return@mapNotNull null
                CryptoMovementRow(kind, out, inEntry, from.name, to.name, results[out.id])
            }
            .sortedWith(compareByDescending<CryptoMovementRow> { it.date }.thenByDescending { it.outEntry.original.createdAt })

        val known = assets.map { it.realized }.fold(FiatMoney.zero(LKR)) { a, b -> a + b }
        return CryptoPortfolio(
            assets = assets,
            realized = known,
            unknownResultCount = assets.sumOf { it.unknownResultCount },
            wallets = wallets.filterNot { it.isArchived },
            openingCosts = openingCosts.filterKeys { it in walletsById },
            movements = movements,
        )
    }

    private class Pool {
        var knownQty: BigDecimal = BigDecimal.ZERO
        var knownCost: BigDecimal = BigDecimal.ZERO
        var unknownQty: BigDecimal = BigDecimal.ZERO

        val held: BigDecimal get() = knownQty + unknownQty

        fun acquire(qty: BigDecimal, cost: BigDecimal?) {
            if (cost == null) unknownQty += qty else {
                knownQty += qty
                knownCost += cost
            }
        }

        /** Removes [qty] at average cost. Returns the LKR cost removed, or null when it can't be known. */
        fun dispose(qty: BigDecimal): BigDecimal? {
            val total = held
            if (total.signum() <= 0 || qty > total) {
                // Selling more than recorded: the missing units have no known cost.
                knownQty = BigDecimal.ZERO
                knownCost = BigDecimal.ZERO
                unknownQty = BigDecimal.ZERO
                return null
            }
            val knownShare = knownQty.divide(total, MATH)
            val knownPart = qty.multiply(knownShare, MATH)
            val unknownPart = qty - knownPart
            val costRemoved = if (knownQty.signum() == 0) BigDecimal.ZERO else knownCost.multiply(knownPart.divide(knownQty, MATH), MATH)
            knownQty -= knownPart
            knownCost -= costRemoved
            unknownQty -= unknownPart
            return if (unknownPart.signum() > 0) null else costRemoved
        }
    }

    private fun calculateAsset(
        currency: Currency,
        wallets: List<Account>,
        active: List<LedgerEntry>,
        openingCosts: Map<AccountId, OpeningCost>,
        internalMoveIn: (TransferId?) -> Transaction?,
        results: MutableMap<TransactionId, RealizedResult>,
    ): AssetPosition {
        val pool = Pool()
        var approximate = false
        var quantityComplete = true
        var realized = BigDecimal.ZERO
        var unknownResults = 0

        data class Event(val date: LocalDate, val order: Int, val createdAt: Instant, val run: () -> Unit)
        val events = mutableListOf<Event>()

        wallets.forEach { wallet ->
            val openingQty = when (val opening = wallet.openingBalance) {
                is OpeningBalance.Known -> opening.amount.toDecimal()
                is OpeningBalance.Approximate -> opening.amount.toDecimal().also { approximate = true }
                OpeningBalance.Unknown -> null.also { quantityComplete = false }
            }
            if (openingQty != null && openingQty.signum() > 0) {
                val cost = openingCosts[wallet.id]
                if (cost?.isApproximate == true) approximate = true
                events += Event(wallet.openingDate, 0, Instant.EPOCH) { pool.acquire(openingQty, cost?.cost?.toDecimal()) }
            }
        }

        val walletIds = wallets.map { it.id }.toSet()
        val openingDates = wallets.associate { it.id to it.openingDate }
        active.map { it.current }
            .filter { it.accountId in walletIds && it.amount.currency == currency && it.date >= openingDates.getValue(it.accountId) }
            .filter { internalMoveIn(it.transferId) == null }
            .forEach { tx ->
                val qty = tx.amount.toDecimal()
                val value = CurrencyConverter.reportingAmount(tx.amount, tx.reportingConversion)
                    ?.takeIf { it.currency == LKR }?.toDecimal()
                val run: () -> Unit = when (tx.type) {
                    TransactionType.TRANSFER_IN, TransactionType.INCOME, TransactionType.REFUND, TransactionType.ADJUSTMENT_IN ->
                        { -> pool.acquire(qty, value) }
                    TransactionType.TRANSFER_OUT, TransactionType.EXPENSE -> { ->
                        val cost = pool.dispose(qty)
                        val result = if (cost != null && value != null) value - cost else null
                        if (result != null) realized += result else unknownResults++
                        results[tx.id] = RealizedResult(result?.let(::lkr))
                    }
                    // A reconciliation loss removes coins without selling them.
                    TransactionType.ADJUSTMENT_OUT -> { -> pool.dispose(qty) }
                }
                events += Event(tx.date, 1, tx.createdAt, run)
            }

        // A move that arrives short loses the difference at average cost (nothing is realized); one that
        // arrives with more adds the extra coins at an unknown cost.
        active.map { it.current }
            .filter { it.type == TransactionType.TRANSFER_OUT && it.accountId in walletIds && it.amount.currency == currency }
            .forEach { out ->
                val inLeg = internalMoveIn(out.transferId) ?: return@forEach
                if (out.date < openingDates.getValue(out.accountId) || inLeg.date < openingDates.getValue(inLeg.accountId)) return@forEach
                val difference = out.amount.toDecimal() - inLeg.amount.toDecimal()
                if (difference.signum() == 0) return@forEach
                events += Event(out.date, 1, out.createdAt) {
                    if (difference.signum() > 0) pool.dispose(difference) else pool.acquire(difference.negate(), null)
                }
            }

        events.sortedWith(compareBy<Event>({ it.date }, { it.order }, { it.createdAt })).forEach { it.run() }

        val unknownQty = pool.unknownQty
        val averageCost = if (unknownQty.signum() == 0 && pool.knownQty.signum() > 0) {
            lkr(pool.knownCost.divide(pool.knownQty, MATH))
        } else {
            null
        }
        return AssetPosition(
            currency = currency,
            wallets = wallets,
            quantity = crypto(pool.held, currency),
            isQuantityComplete = quantityComplete,
            knownCost = lkr(pool.knownCost),
            unknownCostQuantity = crypto(unknownQty, currency),
            averageCost = averageCost,
            isCostApproximate = approximate,
            realized = lkr(realized),
            unknownResultCount = unknownResults,
        )
    }

    private fun lkr(value: BigDecimal): FiatMoney =
        FiatMoney(value.setScale(LKR.fractionDigits, RoundingMode.HALF_EVEN).movePointRight(LKR.fractionDigits).longValueExact(), LKR)

    private fun crypto(value: BigDecimal, currency: Currency): CryptoAmount =
        CryptoAmount.of(value.max(BigDecimal.ZERO).setScale(currency.fractionDigits, RoundingMode.HALF_EVEN), currency)
}
