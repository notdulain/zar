package com.example.zar.features.transactions.domain.income.model

import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import java.math.BigDecimal
import java.time.LocalDate

/** Stored in `Transaction.categoryId` for INCOME records. */
enum class IncomeCategory(val id: String) {
    SALARY("salary"),
    FREELANCE("freelance"),
    ALLOWANCE("allowance"),
    GIFT("gift"),
    OTHER("other"),
    ;

    companion object {
        fun fromId(id: String?): IncomeCategory? = entries.firstOrNull { it.id == id }
    }
}

/** Validated details of money that actually arrived. */
data class IncomeDetails(
    val amount: Money,
    val date: LocalDate,
    /** LKR per unit of [amount]'s currency. Null for LKR income, or when the rate is unknown. */
    val rateToReporting: BigDecimal?,
    val receivedFrom: String?,
    val category: IncomeCategory?,
    val note: String?,
    val time: java.time.LocalTime? = null,
)

/** What the user typed into the income form. */
data class IncomeFormInput(
    val amount: String,
    val currency: Currency,
    val date: LocalDate,
    val rate: String,
    val receivedFrom: String,
    val category: IncomeCategory?,
    val note: String,
    val time: java.time.LocalTime? = null,
)

enum class IncomeProblem {
    AMOUNT_MISSING,
    AMOUNT_INVALID,
    DATE_IN_FUTURE,
    RATE_INVALID,
    RECEIVED_FROM_TOO_LONG,
    NOTE_TOO_LONG,
}

sealed interface IncomeFormResult {
    data class Valid(val details: IncomeDetails) : IncomeFormResult

    data class Invalid(val problems: Set<IncomeProblem>) : IncomeFormResult
}

/**
 * Received income for a screen. [receivedThisMonth] counts INCOME only and reports records
 * without an LKR rate as unresolved instead of guessing.
 */
data class IncomeOverview(
    val receivedThisMonth: ReportingTotal,
    /** Active (not undone) income, newest first. */
    val entries: List<LedgerEntry>,
)
