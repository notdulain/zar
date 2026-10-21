package com.example.zar.features.accounts.domain.reconcile

import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import java.time.Instant
import java.time.LocalDate

/**
 * Pure rules for checking an account's balance. A gap is saved as an explicit adjustment: no category, no
 * invented purchase. An outgoing adjustment counts as unknown spending and keeps the "incomplete" warning,
 * an incoming one only moves the balance (see `LedgerSummaryCalculator`).
 */
object ReconcileRules {

    fun availability(balance: AccountBalance): CheckAvailability = when {
        balance.account.isArchived -> CheckAvailability.ARCHIVED
        balance.current == null -> CheckAvailability.BALANCE_UNKNOWN
        balance.mismatchedCount > 0 -> CheckAvailability.ENTRIES_NEED_FIXING
        else -> CheckAvailability.AVAILABLE
    }

    /** Compares the typed amount with the recorded balance. Zero is a valid count: the cash tin can be empty. */
    fun evaluate(balance: AccountBalance, input: String): CheckResult {
        val recorded = balance.current
        if (availability(balance) != CheckAvailability.AVAILABLE || recorded == null) {
            return CheckResult.Invalid(emptySet())
        }
        if (input.isBlank()) return CheckResult.Invalid(setOf(CheckProblem.AMOUNT_MISSING))
        val counted: Money = Money.parse(input, balance.account.currency)
            ?: return CheckResult.Invalid(setOf(CheckProblem.AMOUNT_INVALID))
        return CheckResult.Valid(BalanceCheck(balance.account, recorded, counted))
    }

    /** The adjustment that closes the gap, or null when the balance already matches. */
    fun newAdjustment(id: TransactionId, check: BalanceCheck, date: LocalDate, createdAt: Instant): Transaction? {
        val type = when (check.outcome) {
            CheckOutcome.MATCHES -> return null
            CheckOutcome.SHORTFALL -> TransactionType.ADJUSTMENT_OUT
            CheckOutcome.SURPLUS -> TransactionType.ADJUSTMENT_IN
        }
        val gap = check.gap
        return Transaction(
            id = id,
            accountId = check.account.id,
            type = type,
            amount = gap,
            date = date,
            reportingConversion = CurrencyConverter.toReporting(gap, null),
            source = RecordSource.RECONCILIATION,
            createdAt = createdAt,
        )
    }
}
