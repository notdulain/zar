package com.example.zar.features.bills.domain

import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillId
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A possible monthly subscription, waiting for the user to confirm or dismiss it. */
data class RecurringSuggestion(
    /** Stable for the same merchant, account, currency and amount, so a dismissal sticks. */
    val key: String,
    val merchant: String,
    val accountId: AccountId,
    val account: Account?,
    val amount: Money,
    val previousDate: LocalDate,
    val lastDate: LocalDate,
    val lastEntryId: TransactionId,
) {
    val nextExpected: LocalDate get() = lastDate.plusMonths(1)
}

/**
 * Suggests monthly bills from spending, conservatively: only after two recorded expenses with
 * the same merchant, account, currency and exact amount, 25 to 35 days apart. It only
 * suggests; nothing is created until the user confirms.
 */
object RecurrenceDetector {
    const val MIN_GAP_DAYS = 25L
    const val MAX_GAP_DAYS = 35L

    /** Lower case letters and digits only, so "NETFLIX.COM" and "Netflix com" match. Too short to trust → null. */
    fun normalize(merchant: String?): String? =
        merchant?.lowercase()?.replace(Regex("[^a-z0-9]+"), " ")?.trim()?.takeIf { it.length >= 3 }

    fun keyFor(accountId: AccountId, merchant: String, amount: Money): String =
        "${accountId.value}|${amount.currency.code}|$merchant|${amount.toDecimal().stripTrailingZeros().toPlainString()}"

    /** The bill a confirmed suggestion becomes. Stable for the key, so confirming twice makes one bill. */
    fun billIdFor(key: String): BillId {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return BillId("sub-" + digest.take(12).joinToString("") { "%02x".format(it) })
    }

    fun suggest(
        entries: List<LedgerEntry>,
        bills: List<Bill>,
        accounts: List<Account>,
        dismissed: Set<String>,
    ): List<RecurringSuggestion> {
        val accountsById = accounts.associateBy { it.id }
        // A merchant already tracked as a bill on that account isn't suggested again.
        val tracked = bills.mapNotNull { bill -> normalize(bill.name)?.let { "${bill.accountId.value}|${bill.amount.currency.code}|$it" } }.toSet()
        // A suggestion already tracked keeps its bill ID even if that bill is renamed later.
        val billIds = bills.map { it.id }.toSet()

        return entries.active()
            .map { it.current }
            .filter { it.type == TransactionType.EXPENSE }
            .mapNotNull { tx -> normalize(tx.merchant)?.let { merchant -> keyFor(tx.accountId, merchant, tx.amount) to tx } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (key, txs) ->
                if (key in dismissed || billIdFor(key) in billIds) return@mapNotNull null
                val first = txs.first()
                val merchant = normalize(first.merchant)!!
                if ("${first.accountId.value}|${first.amount.currency.code}|$merchant" in tracked) return@mapNotNull null
                val sorted = txs.sortedWith(compareBy({ it.date }, { it.createdAt }))
                // The most recent pair of neighbouring charges a month apart.
                val pair = sorted.zipWithNext().lastOrNull { (a, b) ->
                    ChronoUnit.DAYS.between(a.date, b.date) in MIN_GAP_DAYS..MAX_GAP_DAYS
                } ?: return@mapNotNull null
                val (previous, last) = pair
                RecurringSuggestion(
                    key = key,
                    merchant = last.merchant!!.trim(),
                    accountId = last.accountId,
                    account = accountsById[last.accountId],
                    amount = last.amount,
                    previousDate = previous.date,
                    lastDate = last.date,
                    lastEntryId = last.id,
                )
            }
            .sortedByDescending { it.lastDate }
    }
}
