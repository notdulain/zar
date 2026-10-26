package com.example.zar.core.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation destinations. Add one entry per top-level screen.

// Signed-out
@Serializable
data object SignInRoute

@Serializable
data object RegisterRoute

// Signed-in
@Serializable
data object HomeRoute

@Serializable
data object ActivityRoute

@Serializable
data object PlansRoute

@Serializable
data object MoreRoute

@Serializable
data object SettingsRoute

/** Export data: two separate CSV files, actual transactions and expected payments. */
@Serializable
data object ExportRoute

@Serializable
data object IncomeRoute

/** Add income when [incomeId] is null; otherwise edit that income. */
@Serializable
data class IncomeFormRoute(val incomeId: String? = null)

/**
 * Add a manual expense or transfer. [focusNote] opens it with the note focused (Quick note).
 * With [prefillAmount] and [prefillAccountId] it opens as "Add the missing entry": an expense on that
 * account with the gap from a balance check already filled in.
 *
 * This is the one Add-transaction session: Expense, Income and Transfer are tabs of a single sheet, and each
 * keeps its own draft across tab switches until the session is dismissed. [initialKind] picks the tab it opens on
 * ("expense", "income" or "transfer"); the Add menu opens on expense, and More → Received income opens on income.
 */
@Serializable
data class ManualEntryRoute(
    val focusNote: Boolean = false,
    val prefillAmount: String? = null,
    val prefillAccountId: String? = null,
    /** Fills "What was it for?" with a note the person typed into Quick note, when they chose to fill the form instead. */
    val prefillTitle: String? = null,
    val initialKind: String? = null,
)

/** Write a rough note, have it read, and review the records it suggests before anything is saved. */
@Serializable
data object QuickNoteRoute

/** One saved entry in full: its details, corrections and void state. [entryId] is the transaction ID. */
@Serializable
data class EntryDetailRoute(val entryId: String)

/** Correct an expense. The original record is kept; the edit is saved as a correction. */
@Serializable
data class EditEntryRoute(val entryId: String)

/** Link a refund to the purchase [purchaseId] (a transaction ID). */
@Serializable
data class RefundRoute(val purchaseId: String)

/** Saved entries that still need a look, with the weekly catch-up card. */
@Serializable
data object NeedsReviewRoute

/** The weekly cash catch-up: review what is open, count the cash, save at most one adjustment. */
@Serializable
data object CatchUpRoute

/** Day, time and skip for the weekly catch-up reminder. */
@Serializable
data object ReminderSettingsRoute

/** The names Zar was asked to remember a category for, with an editor for each. */
@Serializable
data object CategoryRulesRoute

@Serializable
data object FreelanceRoute

/** New project when [projectId] is null; otherwise edit it. */
@Serializable
data class ProjectFormRoute(val projectId: String? = null)

@Serializable
data class ProjectRoute(val projectId: String)

/** New milestone when [milestoneId] is null; otherwise edit it and link receipts. */
@Serializable
data class MilestoneRoute(val projectId: String, val milestoneId: String? = null)

@Serializable
data object AccountsRoute

@Serializable
data class AccountRoute(val accountId: String)

/** Add an account when [accountId] is null; otherwise edit that account. */
@Serializable
data class AccountFormRoute(val accountId: String? = null)

@Serializable
data object CryptoRoute

@Serializable
data object CryptoMovementRoute

/** Paste or share a bank message; also shows supported banks and capture health. */
@Serializable
data object CaptureRoute

@Serializable
data class CaptureReviewRoute(val draftId: String)

@Serializable
data object BillsRoute

/** New bill when [billId] is null; otherwise edit it. */
@Serializable
data class BillFormRoute(val billId: String? = null)

@Serializable
data class BillRoute(val billId: String)

/** One due date of a bill, where actual payments are linked. [dueDate] is yyyy-MM-dd. */
@Serializable
data class BillOccurrenceRoute(val billId: String, val dueDate: String)
