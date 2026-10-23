package com.example.zar.core.domain.model

import com.example.zar.core.domain.model.money.Money
import java.time.Instant
import java.time.LocalDate

enum class DraftSource { QUICK_NOTE, SMS, SHARED_TEXT }

enum class DraftStatus { PENDING_REVIEW, CONFIRMED, DISCARDED }

enum class DraftField { TYPE, AMOUNT, ACCOUNT, COUNTERPART_ACCOUNT, DATE, CATEGORY, MERCHANT, TITLE }

/**
 * A proposed record awaiting review. Null fields are missing and must be filled by the user;
 * [uncertainFields] were guessed and must be highlighted. Nothing is invented silently.
 */
sealed interface DraftProposal {
    val uncertainFields: Set<DraftField>
    val missingFields: Set<DraftField>

    data class ActualTransaction(
        val type: TransactionType?,
        val amount: Money?,
        val accountId: AccountId?,
        val date: LocalDate?,
        val spendingClass: SpendingClass? = null,
        val categoryId: String? = null,
        val merchant: String? = null,
        val note: String? = null,
        override val uncertainFields: Set<DraftField> = emptySet(),
        /** Descriptive label for the movement, distinct from the merchant's name. */
        val title: String? = null,
    ) : DraftProposal {
        override val missingFields: Set<DraftField>
            get() = buildSet {
                if (type == null) add(DraftField.TYPE)
                if (amount == null) add(DraftField.AMOUNT)
                if (accountId == null) add(DraftField.ACCOUNT)
                if (date == null) add(DraftField.DATE)
            }
    }

    data class Expected(
        val direction: Direction?,
        val amount: Money?,
        val dueDate: LocalDate?,
        val title: String?,
        val accountId: AccountId? = null,
        override val uncertainFields: Set<DraftField> = emptySet(),
    ) : DraftProposal {
        override val missingFields: Set<DraftField>
            get() = buildSet {
                if (direction == null) add(DraftField.TYPE)
                if (amount == null) add(DraftField.AMOUNT)
                if (dueDate == null) add(DraftField.DATE)
                if (title.isNullOrBlank()) add(DraftField.TITLE)
            }
    }

    data class TransferBetweenAccounts(
        val fromAccountId: AccountId?,
        val toAccountId: AccountId?,
        val sent: Money?,
        val received: Money?,
        val date: LocalDate?,
        override val uncertainFields: Set<DraftField> = emptySet(),
    ) : DraftProposal {
        override val missingFields: Set<DraftField>
            get() = buildSet {
                if (fromAccountId == null) add(DraftField.ACCOUNT)
                if (toAccountId == null) add(DraftField.COUNTERPART_ACCOUNT)
                if (sent == null || received == null) add(DraftField.AMOUNT)
                if (date == null) add(DraftField.DATE)
            }
    }
}

/**
 * Unconfirmed proposals from a quick note or captured message. Drafts never affect balances or
 * totals. Raw bank messages are never stored here; they stay in encrypted on-device staging.
 */
data class Draft(
    val id: DraftId,
    val source: DraftSource,
    val proposals: List<DraftProposal>,
    val status: DraftStatus,
    val createdAt: Instant,
) {
    /** Stable ID for the record saved from proposal [index], so repeated confirms save once. */
    fun recordIdFor(index: Int): String = "draft-${id.value}-$index"
}
