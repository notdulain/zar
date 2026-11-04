package com.example.zar.features.crypto.presentation.model

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import com.example.zar.features.crypto.domain.model.CryptoMovementRow
import com.example.zar.features.crypto.domain.model.CryptoPortfolio

/** The one-line status under the Crypto hero (Z89–Z92). */
sealed interface CryptoHeroStatus {
    data object NoWallets : CryptoHeroStatus

    /** Wallets but nothing recorded yet: the totals come from opening balances. */
    data object OpeningBalancesOnly : CryptoHeroStatus

    /** Buys or moves, but nothing sold or traded yet. */
    data object NoSalesYet : CryptoHeroStatus

    /** A sale or a holding is missing a cost or value, so the total covers known results only. */
    data object KnownResultsOnly : CryptoHeroStatus

    data class Realized(val sales: Int, val approximate: Boolean) : CryptoHeroStatus
}

object CryptoOverview {

    fun heroStatus(portfolio: CryptoPortfolio): CryptoHeroStatus {
        val sales = portfolio.movements.count { it.result?.isKnown == true }
        return when {
            portfolio.wallets.isEmpty() -> CryptoHeroStatus.NoWallets
            portfolio.unknownResultCount > 0 || portfolio.assets.any { it.unknownCostQuantity.isPositive } -> CryptoHeroStatus.KnownResultsOnly
            portfolio.movements.isEmpty() -> CryptoHeroStatus.OpeningBalancesOnly
            sales == 0 -> CryptoHeroStatus.NoSalesYet
            else -> CryptoHeroStatus.Realized(sales, approximate = portfolio.assets.any { it.isCostApproximate })
        }
    }

    /** The amount named in the title ("Bought BTC 0.0025"): the coins received for a buy, the amount sent for anything else. */
    fun titleAmount(row: CryptoMovementRow): Money =
        if (row.kind == CryptoMovementKind.BUY) row.inEntry.current.amount else row.outEntry.current.amount

    /** The movement's LKR value: what was paid for a buy, or received for a sale. Null when unknown. */
    fun lkrValue(row: CryptoMovementRow): FiatMoney? =
        CurrencyConverter.reportingAmount(row.outEntry.current.amount, row.outEntry.current.reportingConversion)
            ?: CurrencyConverter.reportingAmount(row.inEntry.current.amount, row.inEntry.current.reportingConversion)

    /** The average cost taken off the holding by a sale or trade: its value less the profit. Null unless both are known. */
    fun costRemoved(row: CryptoMovementRow): FiatMoney? {
        val profit = row.result?.amount ?: return null
        val value = lkrValue(row) ?: return null
        if (value.currency != profit.currency) return null
        return FiatMoney(value.minorUnits - profit.minorUnits, value.currency)
    }
}
