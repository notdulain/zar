package com.example.zar.features.export.domain

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.ReportingConversion
import java.time.format.DateTimeFormatter

/**
 * Actual transactions only: money that really moved. Expected payments are a separate file.
 *
 * - One row per saved transaction, including undone ones, which are marked VOIDED so the history
 *   stays honest. Transfers appear as their two legs, linked by `transfer_id`.
 * - The main columns hold the **current** (corrected) values. `original_amount` and
 *   `original_date` keep what was first saved, and `corrected` says whether they differ.
 * - `reporting_amount_lkr` is the value stored when the record was saved, never today's rate.
 *   When no rate was known it is empty and `reporting_status` says MISSING.
 * - Raw bank messages are never exported: they are not part of a transaction. Evidence is only
 *   counted, never copied.
 */
object TransactionsCsv {

    val HEADER = listOf(
        "transaction_id", "status", "date", "time", "type", "direction",
        "amount", "currency", "reporting_amount_lkr", "reporting_status", "exchange_rate",
        "account_id", "account_name", "spending_class", "category_id", "merchant", "note",
        "source", "transfer_id", "refund_of_transaction_id",
        "corrected", "correction_count", "original_amount", "original_date", "last_corrected_at",
        "voided_at", "linked_evidence_count", "created_at",
    )

    fun build(entries: List<LedgerEntry>, accounts: List<Account>): CsvDocument {
        val accountNames = accounts.associate { it.id to it.name }
        val rows = entries
            .sortedWith(
                compareBy<LedgerEntry>({ it.current.date }, { it.current.time }, { it.original.createdAt }, { it.id.value }),
            )
            .map { entry -> row(entry, accountNames) }
        return Csv.document(HEADER, rows)
    }

    private fun row(entry: LedgerEntry, accountNames: Map<com.example.zar.core.domain.model.AccountId, String>): List<String?> {
        val tx = entry.current
        val conversion = tx.reportingConversion
        val reporting = CurrencyConverter.reportingAmount(tx.amount, conversion)
        return listOf(
            entry.id.value,
            if (entry.isVoided) "VOIDED" else "ACTIVE",
            tx.date.toString(),
            tx.time?.format(TIME),
            tx.type.name,
            tx.direction.name,
            tx.amount.plain(),
            tx.amount.currency.code,
            reporting?.plain(),
            when (conversion) {
                ReportingConversion.SameCurrency -> "SAME"
                is ReportingConversion.Converted -> "CONVERTED"
                is ReportingConversion.Missing -> "MISSING"
            },
            (conversion as? ReportingConversion.Converted)?.rate?.rate?.toPlainString(),
            tx.accountId.value,
            accountNames[tx.accountId],
            tx.spendingClass?.name,
            tx.categoryId,
            tx.merchant,
            tx.note,
            tx.source.name,
            tx.transferId?.value,
            tx.refundOf?.value,
            entry.isCorrected.toString(),
            entry.corrections.size.toString(),
            entry.original.amount.plain(),
            entry.original.date.toString(),
            entry.corrections.lastOrNull()?.correctedAt?.toString(),
            entry.voidMarker?.voidedAt?.toString(),
            entry.evidenceIds.size.toString(),
            entry.original.createdAt.toString(),
        )
    }

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}

/** Exact decimal text with no exponent, e.g. `1250.50` or `0.00012345`. */
internal fun Money.plain(): String = toDecimal().toPlainString()
