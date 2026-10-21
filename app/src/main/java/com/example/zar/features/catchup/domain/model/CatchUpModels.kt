package com.example.zar.features.catchup.domain.model

import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Why a saved entry is still open. An entry is listed once, under the first reason that applies. */
enum class UnresolvedKind {
    /** A cash or balance adjustment that was never explained. It counts as unknown spending. */
    UNEXPLAINED_ADJUSTMENT,

    /** No exchange rate was available, so the entry is missing from the totals. */
    MISSING_RATE,

    /** An expense that was saved without saying what kind of spending it was. */
    UNCATEGORISED,
}

/** [account] is null only when the entry belongs to an account that no longer exists. */
data class UnresolvedItem(val kind: UnresolvedKind, val transaction: Transaction, val account: Account?) {
    val entryId: TransactionId get() = transaction.id
}

/**
 * What the weekly catch-up works from: the open items, the cash accounts to count, and when the person last
 * finished a catch-up. Built only from saved records, so a quiet week is shown as quiet, never as complete.
 */
data class CatchUpOverview(
    val items: List<UnresolvedItem>,
    /** Active cash accounts, with their recorded balances. */
    val cashAccounts: List<AccountBalance>,
    val lastCatchUp: LocalDate?,
    /** Entries dated in the last seven days. Zero means nothing was recorded, not that nothing was spent. */
    val recordedThisWeek: Int,
) {
    val hasNothingRecorded: Boolean get() = recordedThisWeek == 0
}

/** The weekly reminder. The default is Sunday at 7 pm in Sri Lanka time (see `ZarTime.ZONE`). */
data class ReminderSettings(
    val enabled: Boolean = true,
    val day: DayOfWeek = DayOfWeek.SUNDAY,
    val time: LocalTime = LocalTime.of(19, 0),
    /** The date of one reminder the person chose to skip. Later reminders are unaffected. */
    val skippedOccurrence: LocalDate? = null,
)

/** Everything catch-up remembers between launches. */
data class CatchUpPreferences(
    val reminder: ReminderSettings = ReminderSettings(),
    val lastCatchUp: LocalDate? = null,
)
