package com.example.zar.features.crypto.domain.model

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import java.time.Instant
import java.time.LocalDate

/**
 * What a crypto movement is, worked out from its two accounts. Every kind is saved as a
 * transfer between owned accounts, so none of them is ever income or spending.
 */
enum class CryptoMovementKind {
    /** Fiat account to crypto wallet. */
    BUY,

    /** Crypto wallet to fiat account. Realizes a profit or loss. */
    SELL,

    /** One crypto asset for another. Realizes a profit or loss on the asset given up. */
    TRADE,

    /** The same asset between two owned wallets. Cost moves with it; nothing is realized. */
    MOVE,
}

/** A validated crypto movement, ready to save. */
data class CryptoMovement(
    val kind: CryptoMovementKind,
    val from: Account,
    val to: Account,
    val sent: Money,
    val received: Money,
    /** The movement's value in LKR when known. Null means "unknown" and stays visibly missing. */
    val lkrValue: FiatMoney?,
    val date: LocalDate,
    val note: String?,
)

/**
 * What the coins a wallet started with cost, in LKR. Optional: without it, profit or loss on
 * those coins shows as unknown instead of being guessed.
 */
data class OpeningCost(
    val accountId: AccountId,
    val cost: FiatMoney,
    val isApproximate: Boolean,
    val updatedAt: Instant,
)

/** A sale's result. [amount] is null when a cost or value is missing, so it can't be worked out. */
data class RealizedResult(val amount: FiatMoney?) {
    val isKnown: Boolean get() = amount != null
}

/** Holdings of one asset across all wallets, using average cost in LKR. */
data class AssetPosition(
    val currency: Currency,
    val wallets: List<Account>,
    /** Recorded holdings. A minimum when a wallet's opening quantity is unknown. */
    val quantity: Money,
    val isQuantityComplete: Boolean,
    /** LKR cost of the units whose cost is known. */
    val knownCost: FiatMoney,
    /** Units held whose cost is unknown (opening coins without a cost, or moves without a value). */
    val unknownCostQuantity: Money,
    /** LKR per one unit; null unless every unit's cost is known. */
    val averageCost: FiatMoney?,
    val isCostApproximate: Boolean,
    val realized: FiatMoney,
    val unknownResultCount: Int,
)

/** One saved crypto transfer as the screen shows it. */
data class CryptoMovementRow(
    val kind: CryptoMovementKind,
    val outEntry: LedgerEntry,
    val inEntry: LedgerEntry,
    val fromName: String,
    val toName: String,
    /** Set for sells and trades. */
    val result: RealizedResult?,
) {
    val date: LocalDate get() = outEntry.current.date
}

data class CryptoPortfolio(
    val assets: List<AssetPosition>,
    /** Sum of every known realized result, in LKR. Never part of Received. */
    val realized: FiatMoney,
    val unknownResultCount: Int,
    val wallets: List<Account>,
    val openingCosts: Map<AccountId, OpeningCost>,
    /** Newest first. */
    val movements: List<CryptoMovementRow>,
)
