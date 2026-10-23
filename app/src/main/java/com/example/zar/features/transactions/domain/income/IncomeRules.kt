package com.example.zar.features.transactions.domain.income

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.ExchangeRate
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.RateSource
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.transactions.domain.income.model.IncomeDetails
import com.example.zar.features.transactions.domain.income.model.IncomeFormInput
import com.example.zar.features.transactions.domain.income.model.IncomeFormResult
import com.example.zar.features.transactions.domain.income.model.IncomeProblem
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Pure rules for turning the income form into records and corrections. */
object IncomeRules {
    // Limits match firestore.rules.
    const val MAX_RECEIVED_FROM = 120
    const val MAX_NOTE = 500

    fun validate(
        input: IncomeFormInput,
        today: LocalDate,
        reporting: Currency = Currencies.REPORTING,
    ): IncomeFormResult {
        val problems = mutableSetOf<IncomeProblem>()

        val amount = if (input.amount.isBlank()) {
            problems += IncomeProblem.AMOUNT_MISSING
            null
        } else {
            Money.parse(input.amount, input.currency)?.takeIf { it.isPositive }
                ?: null.also { problems += IncomeProblem.AMOUNT_INVALID }
        }

        if (input.date.isAfter(today)) problems += IncomeProblem.DATE_IN_FUTURE

        // A rate only applies to foreign income; blank means "unknown" and stays visibly missing.
        val rate = if (input.currency == reporting || input.rate.isBlank()) {
            null
        } else {
            input.rate.replace(",", "").trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
                ?: null.also { problems += IncomeProblem.RATE_INVALID }
        }

        val receivedFrom = input.receivedFrom.trim()
        if (receivedFrom.length > MAX_RECEIVED_FROM) problems += IncomeProblem.RECEIVED_FROM_TOO_LONG
        val note = input.note.trim()
        if (note.length > MAX_NOTE) problems += IncomeProblem.NOTE_TOO_LONG

        if (problems.isNotEmpty() || amount == null) return IncomeFormResult.Invalid(problems)
        return IncomeFormResult.Valid(
            IncomeDetails(
                amount = amount,
                date = input.date,
                time = input.time,
                rateToReporting = rate,
                receivedFrom = receivedFrom.ifEmpty { null },
                category = input.category,
                note = note.ifEmpty { null },
            ),
        )
    }

    /** The LKR value stored with the record, fixed at save time. */
    fun reportingConversion(
        details: IncomeDetails,
        asOf: Instant,
        reporting: Currency = Currencies.REPORTING,
    ): ReportingConversion {
        val rate = details.rateToReporting
            ?.takeIf { details.amount.currency != reporting }
            ?.let { ExchangeRate(details.amount.currency, reporting, it, asOf, RateSource.MANUAL) }
        return CurrencyConverter.toReporting(details.amount, rate, reporting)
    }

    fun newIncome(
        id: TransactionId,
        account: Account,
        details: IncomeDetails,
        createdAt: Instant,
    ): Transaction = Transaction(
        id = id,
        accountId = account.id,
        type = TransactionType.INCOME,
        amount = details.amount,
        date = details.date,
        time = details.time,
        reportingConversion = reportingConversion(details, createdAt),
        categoryId = details.category?.id,
        merchant = details.receivedFrom,
        note = details.note,
        createdAt = createdAt,
    )

    /**
     * Only the fields the user actually changed. A changed amount keeps the stored historical
     * rate (CorrectTransactionUseCase reconverts it); a changed rate replaces the conversion.
     */
    fun changesBetween(current: Transaction, edited: IncomeDetails, asOf: Instant): TransactionChanges {
        val rateChanged = !sameRate(storedRate(current.reportingConversion), edited.rateToReporting)
        return TransactionChanges(
            amount = edited.amount.takeIf { it != current.amount },
            reportingConversion = if (rateChanged) reportingConversion(edited, asOf) else null,
            date = edited.date.takeIf { it != current.date },
            time = fieldUpdate(current.time, edited.time),
            categoryId = fieldUpdate(current.categoryId, edited.category?.id),
            merchant = fieldUpdate(current.merchant, edited.receivedFrom),
            note = fieldUpdate(current.note, edited.note),
        )
    }

    fun storedRate(conversion: ReportingConversion): BigDecimal? =
        (conversion as? ReportingConversion.Converted)?.rate?.rate

    private fun sameRate(a: BigDecimal?, b: BigDecimal?): Boolean =
        if (a == null || b == null) a == b else a.compareTo(b) == 0

    private fun <T> fieldUpdate(current: T?, edited: T?): FieldUpdate<T>? = when {
        current == edited -> null
        edited == null -> FieldUpdate.Clear
        else -> FieldUpdate.Set(edited)
    }
}
