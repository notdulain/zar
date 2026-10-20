package com.example.zar.features.transactions.domain.manual

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.transactions.domain.manual.model.ExpenseDetails
import com.example.zar.features.transactions.domain.manual.model.ExpenseFormInput
import com.example.zar.features.transactions.domain.manual.model.ExpenseFormResult
import com.example.zar.features.transactions.domain.manual.model.ExpenseProblem
import com.example.zar.features.transactions.domain.manual.model.TransferDetails
import com.example.zar.features.transactions.domain.manual.model.TransferFormInput
import com.example.zar.features.transactions.domain.manual.model.TransferFormResult
import com.example.zar.features.transactions.domain.manual.model.TransferProblem
import java.time.Instant
import java.time.LocalDate

/** Pure rules for turning the manual expense and transfer forms into records. */
object ManualEntryRules {
    // Limits match firestore.rules.
    const val MAX_TITLE = 120
    const val MAX_NOTE = 500

    /** Category and title of the expense that holds a transfer's fee. */
    const val TRANSFER_FEE_CATEGORY_ID = "transfer-fee"
    const val TRANSFER_FEE_TITLE = "Transfer fee"

    fun validateExpense(input: ExpenseFormInput, today: LocalDate): ExpenseFormResult {
        val problems = mutableSetOf<ExpenseProblem>()

        val account = input.account
        if (account == null) problems += ExpenseProblem.ACCOUNT_MISSING

        val amount = if (input.amount.isBlank()) {
            problems += ExpenseProblem.AMOUNT_MISSING
            null
        } else {
            Money.parse(input.amount, account?.currency ?: Currencies.REPORTING)?.takeIf { it.isPositive }
                ?: null.also { problems += ExpenseProblem.AMOUNT_INVALID }
        }

        if (input.category == null) problems += ExpenseProblem.CATEGORY_MISSING
        if (input.date.isAfter(today)) problems += ExpenseProblem.DATE_IN_FUTURE

        val title = input.title.trim()
        if (title.length > MAX_TITLE) problems += ExpenseProblem.TITLE_TOO_LONG
        val description = input.description.trim()
        if (description.length > MAX_NOTE) problems += ExpenseProblem.DESCRIPTION_TOO_LONG

        if (problems.isNotEmpty() || account == null || amount == null || input.category == null) {
            return ExpenseFormResult.Invalid(problems)
        }
        return ExpenseFormResult.Valid(
            ExpenseDetails(
                account = account,
                amount = amount,
                date = input.date,
                category = input.category,
                spendingClass = input.spendingClass,
                title = title.ifEmpty { null },
                description = description.ifEmpty { null },
                time = input.time,
            ),
        )
    }

    fun validateTransfer(input: TransferFormInput, today: LocalDate): TransferFormResult {
        val problems = mutableSetOf<TransferProblem>()
        val from = input.from
        val to = input.to

        if (from == null) problems += TransferProblem.FROM_MISSING
        if (to == null) problems += TransferProblem.TO_MISSING
        if (from != null && to != null) {
            when {
                from.id == to.id -> problems += TransferProblem.SAME_ACCOUNT
                from.currency != to.currency -> problems += TransferProblem.DIFFERENT_CURRENCIES
            }
        }

        val currency = from?.currency ?: Currencies.REPORTING
        val amount = if (input.amount.isBlank()) {
            problems += TransferProblem.AMOUNT_MISSING
            null
        } else {
            Money.parse(input.amount, currency)?.takeIf { it.isPositive }
                ?: null.also { problems += TransferProblem.AMOUNT_INVALID }
        }

        // A blank or zero fee means "no fee". Anything else must be a positive amount.
        val fee = if (input.fee.isBlank()) {
            null
        } else {
            when (val parsed = Money.parse(input.fee, currency)) {
                null -> null.also { problems += TransferProblem.FEE_INVALID }
                else -> when {
                    parsed.isNegative -> null.also { problems += TransferProblem.FEE_INVALID }
                    parsed.isZero -> null
                    else -> parsed
                }
            }
        }

        if (amount != null && fee != null && runCatching { amount + fee }.isFailure) {
            problems += TransferProblem.FEE_INVALID
        }
        if (input.date.isAfter(today)) problems += TransferProblem.DATE_IN_FUTURE
        val note = input.note.trim()
        if (note.length > MAX_NOTE) problems += TransferProblem.NOTE_TOO_LONG

        if (problems.isNotEmpty() || from == null || to == null || amount == null) {
            return TransferFormResult.Invalid(problems)
        }
        return TransferFormResult.Valid(
            TransferDetails(from = from, to = to, amount = amount, fee = fee, date = input.date, note = note.ifEmpty { null }, time = input.time),
        )
    }

    /**
     * The LKR value is fixed at save time. LKR amounts convert to themselves; any other currency is
     * saved with a missing rate so totals report it as unresolved instead of guessing.
     */
    fun newExpense(id: TransactionId, details: ExpenseDetails, createdAt: Instant): Transaction = Transaction(
        id = id,
        accountId = details.account.id,
        type = TransactionType.EXPENSE,
        amount = details.amount,
        date = details.date,
        reportingConversion = CurrencyConverter.toReporting(details.amount, null),
        spendingClass = details.spendingClass,
        categoryId = details.category.id,
        merchant = details.title,
        note = details.description,
        createdAt = createdAt,
        time = details.time,
    )

    fun newTransfer(id: TransferId, details: TransferDetails, createdAt: Instant): Transfer {
        val conversion = CurrencyConverter.toReporting(details.amount, null)
        return Transfer(
            id = id,
            fromAccountId = details.from.id,
            toAccountId = details.to.id,
            sent = details.amount,
            received = details.amount,
            date = details.date,
            sentConversion = conversion,
            receivedConversion = conversion,
            fee = newTransferFee(id, details, createdAt),
            note = details.note,
            createdAt = createdAt,
            time = details.time,
        )
    }

    /** ID of the expense holding a transfer's fee. Stable, so a retry can't charge the fee twice. */
    fun feeId(transferId: TransferId): TransactionId = TransactionId("${transferId.value}-fee")

    /** The fee as its own expense on the sending account, or null when there is no fee. */
    fun newTransferFee(transferId: TransferId, details: TransferDetails, createdAt: Instant): Transaction? {
        val fee = details.fee ?: return null
        return Transaction(
            id = feeId(transferId),
            accountId = details.from.id,
            type = TransactionType.EXPENSE,
            amount = fee,
            date = details.date,
            reportingConversion = CurrencyConverter.toReporting(fee, null),
            spendingClass = SpendingClass.COMMITTED,
            categoryId = TRANSFER_FEE_CATEGORY_ID,
            merchant = TRANSFER_FEE_TITLE,
            createdAt = createdAt,
            time = details.time,
        )
    }
}
