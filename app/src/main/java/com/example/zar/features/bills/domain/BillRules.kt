package com.example.zar.features.bills.domain

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillReminder
import java.time.LocalDate

enum class BillProblem {
    NAME_MISSING,
    NAME_TOO_LONG,
    ACCOUNT_MISSING,
    AMOUNT_MISSING,
    AMOUNT_INVALID,
    /** Linking more than the expense has left. */
    MORE_THAN_PAYMENT,
    /** Linking more than the due date still expects. */
    MORE_THAN_REMAINING,
}

data class BillInput(
    val name: String,
    val account: Account,
    val amount: Money,
    val frequency: BillFrequency,
    val firstDue: LocalDate,
    val reminder: BillReminder? = null,
)

sealed interface BillCheck<out T> {
    data class Valid<T>(val value: T) : BillCheck<T>

    data class Invalid(val problems: Set<BillProblem>) : BillCheck<Nothing>
}

/** Pure validation for the bill forms. Limits match firestore.rules. */
object BillRules {
    const val MAX_NAME = 80

    fun checkBill(
        name: String,
        account: Account?,
        amount: String,
        frequency: BillFrequency,
        firstDue: LocalDate,
        reminder: BillReminder? = null,
    ): BillCheck<BillInput> {
        val problems = mutableSetOf<BillProblem>()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) problems += BillProblem.NAME_MISSING
        if (trimmed.length > MAX_NAME) problems += BillProblem.NAME_TOO_LONG
        if (account == null) problems += BillProblem.ACCOUNT_MISSING
        val money = account?.let { parsePositive(amount, it, problems) }
        if (account == null && amount.isBlank()) problems += BillProblem.AMOUNT_MISSING
        return if (problems.isEmpty() && account != null && money != null) {
            BillCheck.Valid(BillInput(trimmed, account, money, frequency, firstDue, reminder))
        } else {
            BillCheck.Invalid(problems)
        }
    }

    /** The part of an expense to link: within what it has left and what the due date still expects. */
    fun checkLink(amount: String, available: Money, remaining: Money): BillCheck<Money> {
        val problems = mutableSetOf<BillProblem>()
        val money = if (amount.isBlank()) {
            null.also { problems += BillProblem.AMOUNT_MISSING }
        } else {
            Money.parse(amount, available.currency)?.takeIf { it.isPositive } ?: null.also { problems += BillProblem.AMOUNT_INVALID }
        }
        if (money != null) {
            if (money > available) problems += BillProblem.MORE_THAN_PAYMENT
            if (money > remaining) problems += BillProblem.MORE_THAN_REMAINING
        }
        return if (problems.isEmpty() && money != null) BillCheck.Valid(money) else BillCheck.Invalid(problems)
    }

    private fun parsePositive(text: String, account: Account, problems: MutableSet<BillProblem>): Money? {
        if (text.isBlank()) {
            problems += BillProblem.AMOUNT_MISSING
            return null
        }
        return Money.parse(text, account.currency)?.takeIf { it.isPositive } ?: null.also { problems += BillProblem.AMOUNT_INVALID }
    }
}
