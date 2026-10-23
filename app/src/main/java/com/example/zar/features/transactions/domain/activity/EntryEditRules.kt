package com.example.zar.features.transactions.domain.activity

import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.money.Money
import java.time.LocalTime
import java.time.LocalDate

/** What the correction form holds. Starts as the entry's current values. */
data class ExpenseEditInput(
    val amount: String,
    val date: LocalDate,
    /** Null leaves the entry without a category (for example an imported record that has none yet). */
    val categoryId: String?,
    val spendingClass: SpendingClass,
    /** Saved as the entry's title (merchant). */
    val title: String,
    val description: String,
    val time: LocalTime? = null,
) {
    companion object {
        fun from(tx: Transaction) = ExpenseEditInput(
            amount = tx.amount.toDecimal().stripTrailingZeros().toPlainString(),
            date = tx.date,
            time = tx.time,
            categoryId = tx.categoryId,
            spendingClass = tx.spendingClass ?: SpendingClass.UNKNOWN,
            title = tx.merchant.orEmpty(),
            description = tx.note.orEmpty(),
        )
    }
}

enum class EditProblem {
    AMOUNT_MISSING,
    AMOUNT_INVALID,
    DATE_IN_FUTURE,
    TITLE_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    NOTHING_CHANGED,
}

sealed interface EditResult {
    /** Only the fields that changed. The account and currency can never be corrected. */
    data class Valid(val changes: TransactionChanges) : EditResult

    data class Invalid(val problems: Set<EditProblem>) : EditResult
}

/** Pure rules for turning the correction form into a [TransactionChanges]. */
object EntryEditRules {
    // Limits match firestore.rules.
    const val MAX_TITLE = 120
    const val MAX_NOTE = 500

    fun validate(entry: LedgerEntry, input: ExpenseEditInput, today: LocalDate): EditResult {
        val current = entry.current
        val problems = mutableSetOf<EditProblem>()

        val amount: Money? = if (input.amount.isBlank()) {
            problems += EditProblem.AMOUNT_MISSING
            null
        } else {
            Money.parse(input.amount, current.amount.currency)?.takeIf { it.isPositive }
                ?: null.also { problems += EditProblem.AMOUNT_INVALID }
        }
        if (input.date.isAfter(today)) problems += EditProblem.DATE_IN_FUTURE
        val title = input.title.trim()
        if (title.length > MAX_TITLE) problems += EditProblem.TITLE_TOO_LONG
        val description = input.description.trim()
        if (description.length > MAX_NOTE) problems += EditProblem.DESCRIPTION_TOO_LONG
        if (problems.isNotEmpty() || amount == null) return EditResult.Invalid(problems)

        // The amount keeps its stored historical rate: CorrectTransactionUseCase reconverts it.
        val changes = TransactionChanges(
            amount = amount.takeIf { it != current.amount },
            date = input.date.takeIf { it != current.date },
            time = fieldUpdate(current.time, input.time),
            spendingClass = input.spendingClass.takeIf { it != current.spendingClass },
            categoryId = fieldUpdate(current.categoryId, input.categoryId),
            merchant = fieldUpdate(current.merchant, title.ifEmpty { null }),
            note = fieldUpdate(current.note, description.ifEmpty { null }),
        )
        return if (changes.isEmpty) EditResult.Invalid(setOf(EditProblem.NOTHING_CHANGED)) else EditResult.Valid(changes)
    }

    private fun <T> fieldUpdate(current: T?, edited: T?): FieldUpdate<T>? = when {
        current == edited -> null
        edited == null -> FieldUpdate.Clear
        else -> FieldUpdate.Set(edited)
    }
}
