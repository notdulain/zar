package com.example.zar.features.transactions.domain.activity.model

import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.transactions.domain.refund.RefundSummary
import java.time.LocalTime
import java.time.LocalDate

enum class DatePreset { THIS_MONTH, LAST_7_DAYS, LAST_MONTH, CUSTOM }

/** What the Activity list is narrowed to. The default is everything dated this month, voided entries hidden. */
data class ActivityFilter(
    val accountId: AccountId? = null,
    val preset: DatePreset = DatePreset.THIS_MONTH,
    /** Used only with [DatePreset.CUSTOM]. */
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val showVoided: Boolean = false,
    val query: String = "",
) {
    /** How many choices differ from the default. The filter button shows a dot when this is above zero. */
    val activeCount: Int
        get() = listOf(
            accountId != null,
            preset != DatePreset.THIS_MONTH,
            showVoided,
            query.isNotBlank(),
        ).count { it }
}

/** Everything Activity needs from storage at one moment. */
data class ActivitySnapshot(
    val entries: List<LedgerEntry>,
    /** Includes archived accounts, so old entries still show where they were recorded. */
    val accounts: List<Account>,
)

data class ActivityRow(
    val entry: LedgerEntry,
    /** Null only if the account no longer exists. */
    val account: Account?,
)

data class ActivityList(
    val range: ClosedRange<LocalDate>,
    val rows: List<ActivityRow>,
    /** Received and Spent for the same account and dates as the rows, by the shared summary rules. */
    val summary: PeriodSummary,
    /** False only when there are no entries at all, which is different from "nothing matches the filters". */
    val hasAnyEntries: Boolean,
)

/** Which form, if any, can correct this entry. */
enum class EditKind {
    NONE,

    /** Expenses use the correction form from this feature. */
    EXPENSE,

    /** Income keeps its own form from the income feature. */
    INCOME,
}

/** One entry with everything the detail screen shows. */
data class EntryDetail(
    val entry: LedgerEntry,
    val account: Account?,
    /** The other leg when this entry is one half of a transfer. */
    val counterpart: LedgerEntry?,
    val counterpartAccount: Account?,
    /** The separate expense holding this transfer's fee, if it has one. */
    val fee: LedgerEntry?,
    /** Applied corrections, oldest first. */
    val history: List<HistoryEvent>,
    val editKind: EditKind,
    val canVoid: Boolean,
    /** For an expense: its linked refunds and what can still be refunded. Null for every other kind of entry. */
    val refunds: RefundSummary? = null,
    /** Whether a refund can be started from here: an active expense with something left, on an account that accepts entries. */
    val canRefund: Boolean = false,
    /** For a refund: the purchase it belongs to, and that purchase's account. */
    val refundedPurchase: LedgerEntry? = null,
    val refundedPurchaseAccount: Account? = null,
    val accounts: List<Account> = emptyList(),
    val canChangeAccount: Boolean = true,
) {
    val isTransfer: Boolean get() = entry.original.transferId != null

    /** An expense with active refunds cannot be undone until those are undone first. */
    val hasActiveRefunds: Boolean get() = refunds?.hasRefunds == true
}

enum class CorrectedField { AMOUNT, DATE, TIME, ACCOUNT, CATEGORY, SPENDING_CLASS, TITLE, NOTE }

/** A field value before or after a correction. The screen decides how to word each kind. */
sealed interface FieldValue {
    data class Amount(val money: Money) : FieldValue
    data class Day(val date: LocalDate) : FieldValue
    data class Time(val time: LocalTime?) : FieldValue
    data class Text(val text: String?) : FieldValue
    data class AccountName(val name: String) : FieldValue
    data class Class(val spendingClass: SpendingClass?) : FieldValue
}

data class FieldChange(val field: CorrectedField, val before: FieldValue, val after: FieldValue)

/** One correction and what it changed compared with the state just before it. */
data class HistoryEvent(val correction: Correction, val changes: List<FieldChange>)
