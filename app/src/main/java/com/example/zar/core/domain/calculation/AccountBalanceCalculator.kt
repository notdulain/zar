package com.example.zar.core.domain.calculation

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Money
import java.time.LocalDate

data class AccountBalance(
    val account: Account,
    /** Recorded movements since the opening date (in minus out). */
    val netMovement: Money,
    /** Opening funds plus movements, or null when the opening value is unknown. */
    val current: Money?,
    val isApproximate: Boolean,
    /** Entries on this account in another currency; they are skipped and need fixing. */
    val mismatchedCount: Int,
)

/** Balance = opening funds + active entries dated on or after the opening date, up to [asOf]. */
object AccountBalanceCalculator {

    fun calculate(account: Account, entries: List<LedgerEntry>, asOf: LocalDate? = null): AccountBalance {
        var net = Money.zero(account.currency)
        var mismatched = 0
        entries.active()
            .map { it.current }
            .filter { it.accountId == account.id && it.date >= account.openingDate && (asOf == null || it.date <= asOf) }
            .forEach { tx ->
                if (tx.amount.currency != account.currency) {
                    mismatched++
                } else {
                    net = if (tx.direction == Direction.IN) net + tx.amount else net - tx.amount
                }
            }
        val (current, approximate) = when (val opening = account.openingBalance) {
            is OpeningBalance.Known -> (opening.amount + net) to false
            is OpeningBalance.Approximate -> (opening.amount + net) to true
            OpeningBalance.Unknown -> null to true
        }
        return AccountBalance(account, net, current, approximate, mismatched)
    }
}
