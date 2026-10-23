package com.example.zar.core.data.remote.firestore

import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.EvidenceId
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.money.AssetKind
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.ExchangeRate
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.RateSource
import com.example.zar.core.domain.model.money.ReportingConversion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Converts ledger records to and from plain Firestore maps. Fiat is stored as integer minor
 * units; crypto quantities and rates as decimal strings, so no precision is lost. Dates are
 * ISO strings (yyyy-MM-dd) and instants are epoch milliseconds.
 *
 * Readers return null for malformed documents so one bad record cannot break the ledger.
 */
object LedgerDocumentMapper {
    private val transactionTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

    // --- Money -----------------------------------------------------------------------------

    fun moneyToMap(money: Money): Map<String, Any> {
        val base = mapOf(
            "currency" to money.currency.code,
            "kind" to money.currency.kind.name,
            "digits" to money.currency.fractionDigits.toLong(),
        )
        return when (money) {
            is FiatMoney -> base + ("minor" to money.minorUnits)
            is CryptoAmount -> base + ("quantity" to money.quantity.toPlainString())
        }
    }

    fun moneyFromMap(map: Map<*, *>?): Money? = runCatching {
        map ?: return null
        val currency = currencyFromMap(map) ?: return null
        when (currency.kind) {
            AssetKind.FIAT -> FiatMoney((map["minor"] as Number).toLong(), currency)
            AssetKind.CRYPTO -> CryptoAmount.of((map["quantity"] as String).toBigDecimal(), currency)
        }
    }.getOrNull()

    private fun currencyFromMap(map: Map<*, *>): Currency? = runCatching {
        Currency(
            code = map["currency"] as String,
            kind = AssetKind.valueOf(map["kind"] as String),
            fractionDigits = (map["digits"] as Number).toInt(),
        )
    }.getOrNull()

    // --- Conversion ------------------------------------------------------------------------

    fun conversionToMap(conversion: ReportingConversion): Map<String, Any> = when (conversion) {
        ReportingConversion.SameCurrency -> mapOf("status" to "SAME")
        is ReportingConversion.Missing -> mapOf("status" to "MISSING", "target" to moneyToMap(FiatMoney.zero(conversion.target)))
        is ReportingConversion.Converted -> mapOf(
            "status" to "CONVERTED",
            "amount" to moneyToMap(conversion.amount),
            "rate" to mapOf(
                "base" to moneyToMap(Money.zero(conversion.rate.base)),
                "quote" to moneyToMap(Money.zero(conversion.rate.quote)),
                "rate" to conversion.rate.rate.toPlainString(),
                "asOf" to conversion.rate.asOf.toEpochMilli(),
                "source" to conversion.rate.source.name,
            ),
        )
    }

    fun conversionFromMap(map: Map<*, *>?): ReportingConversion? = runCatching {
        map ?: return null
        when (map["status"]) {
            "SAME" -> ReportingConversion.SameCurrency
            "MISSING" -> ReportingConversion.Missing(moneyFromMap(map["target"] as Map<*, *>)!!.currency)
            "CONVERTED" -> {
                val rate = map["rate"] as Map<*, *>
                ReportingConversion.Converted(
                    rate = ExchangeRate(
                        base = moneyFromMap(rate["base"] as Map<*, *>)!!.currency,
                        quote = moneyFromMap(rate["quote"] as Map<*, *>)!!.currency,
                        rate = (rate["rate"] as String).toBigDecimal(),
                        asOf = Instant.ofEpochMilli((rate["asOf"] as Number).toLong()),
                        source = RateSource.valueOf(rate["source"] as String),
                    ),
                    amount = moneyFromMap(map["amount"] as Map<*, *>) as FiatMoney,
                )
            }
            else -> null
        }
    }.getOrNull()

    // --- Transaction -----------------------------------------------------------------------

    fun transactionToMap(tx: Transaction): Map<String, Any?> = mapOf(
        "id" to tx.id.value,
        "accountId" to tx.accountId.value,
        "type" to tx.type.name,
        "amount" to moneyToMap(tx.amount),
        "date" to tx.date.toString(),
        "time" to tx.time?.format(transactionTimeFormat),
        "conversion" to conversionToMap(tx.reportingConversion),
        "spendingClass" to tx.spendingClass?.name,
        "categoryId" to tx.categoryId,
        "merchant" to tx.merchant,
        "note" to tx.note,
        "source" to tx.source.name,
        "transferId" to tx.transferId?.value,
        "refundOf" to tx.refundOf?.value,
        "appliedRuleId" to tx.appliedRuleId,
        "appliedRuleVersion" to tx.appliedRuleVersion,
        "createdAt" to tx.createdAt.toEpochMilli(),
    ).filterValues { it != null }

    fun transactionFromMap(map: Map<String, Any?>): Transaction? = runCatching {
        Transaction(
            id = TransactionId(map["id"] as String),
            accountId = AccountId(map["accountId"] as String),
            type = TransactionType.valueOf(map["type"] as String),
            amount = moneyFromMap(map["amount"] as Map<*, *>)!!,
            date = LocalDate.parse(map["date"] as String),
            time = (map["time"] as String?)?.let { LocalTime.parse(it, transactionTimeFormat) },
            reportingConversion = conversionFromMap(map["conversion"] as Map<*, *>)!!,
            spendingClass = (map["spendingClass"] as String?)?.let(SpendingClass::valueOf),
            categoryId = map["categoryId"] as String?,
            merchant = map["merchant"] as String?,
            note = map["note"] as String?,
            source = RecordSource.valueOf(map["source"] as String),
            transferId = (map["transferId"] as String?)?.let(::TransferId),
            refundOf = (map["refundOf"] as String?)?.let(::TransactionId),
            createdAt = Instant.ofEpochMilli((map["createdAt"] as Number).toLong()),
            appliedRuleId = map["appliedRuleId"] as String?,
            appliedRuleVersion = (map["appliedRuleVersion"] as Number?)?.toInt(),
        )
    }.getOrNull()

    // --- Correction ------------------------------------------------------------------------

    /** Changed fields only. A key present with a null value means "cleared". */
    fun correctionToMap(correction: Correction): Map<String, Any?> {
        val c = correction.changes
        val changes = buildMap<String, Any?> {
            c.amount?.let { put("amount", moneyToMap(it)) }
            c.accountId?.let { put("accountId", it.value) }
            c.reportingConversion?.let { put("conversion", conversionToMap(it)) }
            c.date?.let { put("date", it.toString()) }
            c.time?.let { put("time", (it as? FieldUpdate.Set)?.value?.format(transactionTimeFormat)) }
            c.spendingClass?.let { put("spendingClass", it.name) }
            c.categoryId?.let { put("categoryId", it.valueOrNull()) }
            c.merchant?.let { put("merchant", it.valueOrNull()) }
            c.note?.let { put("note", it.valueOrNull()) }
        }
        return mapOf(
            "id" to correction.id.value,
            "transactionId" to correction.transactionId.value,
            "changes" to changes,
            "correctedAt" to correction.correctedAt.toEpochMilli(),
            "reason" to correction.reason,
        ).filterValues { it != null }
    }

    fun correctionFromMap(map: Map<String, Any?>): Correction? = runCatching {
        val changes = map["changes"] as Map<*, *>
        Correction(
            id = CorrectionId(map["id"] as String),
            transactionId = TransactionId(map["transactionId"] as String),
            changes = TransactionChanges(
                accountId = (changes["accountId"] as String?)?.let(::AccountId),
                amount = (changes["amount"] as Map<*, *>?)?.let { moneyFromMap(it)!! },
                reportingConversion = (changes["conversion"] as Map<*, *>?)?.let { conversionFromMap(it)!! },
                date = (changes["date"] as String?)?.let(LocalDate::parse),
                time = when {
                    !changes.containsKey("time") -> null
                    changes["time"] == null -> FieldUpdate.Clear
                    else -> FieldUpdate.Set(LocalTime.parse(changes["time"] as String, transactionTimeFormat))
                },
                spendingClass = (changes["spendingClass"] as String?)?.let(SpendingClass::valueOf),
                categoryId = changes.fieldUpdate("categoryId"),
                merchant = changes.fieldUpdate("merchant"),
                note = changes.fieldUpdate("note"),
            ),
            correctedAt = Instant.ofEpochMilli((map["correctedAt"] as Number).toLong()),
            reason = map["reason"] as String?,
        )
    }.getOrNull()

    private fun FieldUpdate<String>.valueOrNull(): String? = (this as? FieldUpdate.Set)?.value

    private fun Map<*, *>.fieldUpdate(key: String): FieldUpdate<String>? = when {
        !containsKey(key) -> null
        get(key) == null -> FieldUpdate.Clear
        else -> FieldUpdate.Set(get(key) as String)
    }

    // --- Void marker and evidence link ---------------------------------------------------

    fun voidToMap(marker: VoidMarker): Map<String, Any?> = mapOf(
        "transactionId" to marker.transactionId.value,
        "voidedAt" to marker.voidedAt.toEpochMilli(),
        "reason" to marker.reason,
    ).filterValues { it != null }

    fun voidFromMap(map: Map<String, Any?>): VoidMarker? = runCatching {
        VoidMarker(
            transactionId = TransactionId(map["transactionId"] as String),
            voidedAt = Instant.ofEpochMilli((map["voidedAt"] as Number).toLong()),
            reason = map["reason"] as String?,
        )
    }.getOrNull()

    fun evidenceLinkToMap(link: EvidenceLink): Map<String, Any> = mapOf(
        "evidenceId" to link.evidenceId.value,
        "transactionId" to link.transactionId.value,
        "linkedAt" to link.linkedAt.toEpochMilli(),
    )

    fun evidenceLinkFromMap(map: Map<String, Any?>): EvidenceLink? = runCatching {
        EvidenceLink(
            evidenceId = EvidenceId(map["evidenceId"] as String),
            transactionId = TransactionId(map["transactionId"] as String),
            linkedAt = Instant.ofEpochMilli((map["linkedAt"] as Number).toLong()),
        )
    }.getOrNull()
}
