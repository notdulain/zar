package com.example.zar.features.quicknote.domain.model

import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.money.Currency
import java.time.LocalDate

object QuickNoteLimits {
    /** The service rejects longer notes before any provider call. */
    const val MAX_NOTE_LENGTH = 500
    const val MAX_ACCOUNTS = 30
    const val MAX_ACCOUNT_NAME_LENGTH = 80
    const val MAX_PROPOSALS = 5

    /** The caller stops waiting after this long and falls back to manual entry. */
    const val GENERATION_BUDGET_MILLIS = 8_000L
}

/**
 * The only account facts sent to the service: no balances, no opening balances, no archived accounts.
 * The service uses them to match "card" or "BOC" in the note to an account.
 */
data class QuickNoteAccountContext(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    val currency: Currency,
)

/**
 * What the app sends for one interpretation. [note] is the caller's text: it exists only for the active
 * manual or review interaction and is never stored by this feature. [today] is the Asia/Colombo calendar day.
 */
data class QuickNoteDraftRequest(
    val note: String,
    val today: LocalDate,
    val accounts: List<QuickNoteAccountContext>,
) {
    init {
        require(note.isNotBlank() && note.length <= QuickNoteLimits.MAX_NOTE_LENGTH) { "Invalid note length" }
        require(accounts.size <= QuickNoteLimits.MAX_ACCOUNTS) { "Too many accounts" }
    }
}
