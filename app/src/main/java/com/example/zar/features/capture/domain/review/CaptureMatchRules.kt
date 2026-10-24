package com.example.zar.features.capture.domain.review

import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.EvidenceId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** How sure Zar is that a captured message is an entry the person already has. */
enum class MatchStrength { LIKELY, POSSIBLE }

/** An existing entry that a captured message may be a second report of. */
data class DuplicateMatch(val entry: LedgerEntry, val strength: MatchStrength)

/** The entry on the account that the message's own movement would match, and the entry on the other side of it. */
data class TransferMatch(val leg: LedgerEntry, val other: LedgerEntry?)

/**
 * Pure rules that decide what a captured bank message may be a second report of. They only ever suggest: the
 * person answers, and nothing here saves, links or hides anything. Two purchases of the same amount are both
 * real until the person says otherwise.
 */
object CaptureMatchRules {
    /** A payment shows up in the bank message and in the entry within this many days of each other. */
    const val DUPLICATE_WINDOW_DAYS = 2L

    /** Both sides of a transfer can be dated a little apart (cut-off times, weekends). */
    const val TRANSFER_WINDOW_DAYS = 3L

    private const val MAX_SHOWN = 3

    private val DUPLICATE_TYPES = setOf(TransactionType.EXPENSE, TransactionType.INCOME)

    /** Words too common to say two names are the same shop. */
    private val COMMON_WORDS = setOf(
        "the", "and", "lka", "pvt", "ltd", "store", "shop", "super", "mart", "online", "payment", "pos", "card",
        "cafe", "restaurant", "bank", "colombo",
    )

    private val notAlphanumeric = Regex("[^a-z0-9]+")

    fun proposalOf(draft: Draft): DraftProposal.ActualTransaction? = draft.proposals.singleOrNull() as? DraftProposal.ActualTransaction

    /**
     * Which way the money moved. The message's own direction wins; an older draft without one falls back to the
     * type the person has chosen, since the type of a debit or credit is only a guess until then.
     */
    fun directionOf(draft: Draft, chosen: TransactionType? = null): Direction? {
        val proposal = proposalOf(draft) ?: return null
        return proposal.direction ?: chosen?.direction
    }

    /**
     * Existing income or spending that has the same amount and direction, dated within [DUPLICATE_WINDOW_DAYS].
     * It is LIKELY when it is on the chosen account or the names agree, otherwise only POSSIBLE. Entries that
     * were undone, the draft's own record, and entries already linked to this message are never offered.
     */
    fun possibleDuplicates(draft: Draft, accountId: AccountId?, entries: List<LedgerEntry>, chosen: TransactionType? = null): List<DuplicateMatch> {
        val proposal = proposalOf(draft) ?: return emptyList()
        val amount = proposal.amount ?: return emptyList()
        val date = proposal.date ?: return emptyList()
        val direction = directionOf(draft, chosen) ?: return emptyList()
        val own = draft.recordIdFor(0)
        val evidence = EvidenceId(draft.id.value)
        return entries.active()
            .filter { entry ->
                val saved = entry.current
                saved.type in DUPLICATE_TYPES && saved.direction == direction && saved.amount == amount &&
                    daysApart(saved.date, date) <= DUPLICATE_WINDOW_DAYS && entry.id.value != own && evidence !in entry.evidenceIds
            }
            .map { entry ->
                val saved = entry.current
                val sameAccount = accountId != null && saved.accountId == accountId
                val strength = if (sameAccount || sharesName(proposal.merchant, saved.merchant ?: saved.note)) MatchStrength.LIKELY else MatchStrength.POSSIBLE
                DuplicateMatch(entry, strength)
            }
            .sortedWith(compareBy<DuplicateMatch> { it.strength }.thenBy { daysApart(it.entry.current.date, date) }.thenByDescending { it.entry.current.createdAt })
            .take(MAX_SHOWN)
    }

    /**
     * Transfer legs that this message would be the bank's record of: the same amount, dated within
     * [TRANSFER_WINDOW_DAYS], on the chosen account when there is one. Money in matches an incoming leg and money
     * out an outgoing one, so linking never needs a second posting.
     */
    fun transferCounterparts(draft: Draft, accountId: AccountId?, entries: List<LedgerEntry>, chosen: TransactionType? = null): List<TransferMatch> {
        val proposal = proposalOf(draft) ?: return emptyList()
        val amount = proposal.amount ?: return emptyList()
        val date = proposal.date ?: return emptyList()
        val direction = directionOf(draft, chosen) ?: return emptyList()
        val wanted = if (direction == Direction.IN) TransactionType.TRANSFER_IN else TransactionType.TRANSFER_OUT
        val evidence = EvidenceId(draft.id.value)
        val live = entries.active()
        return live
            .filter { entry ->
                val saved = entry.current
                saved.type == wanted && saved.amount == amount && daysApart(saved.date, date) <= TRANSFER_WINDOW_DAYS &&
                    (accountId == null || saved.accountId == accountId) && evidence !in entry.evidenceIds
            }
            .sortedWith(compareBy<LedgerEntry> { daysApart(it.current.date, date) }.thenByDescending { it.current.createdAt })
            .take(MAX_SHOWN)
            .map { leg ->
                val transferId = leg.current.transferId
                TransferMatch(leg, live.firstOrNull { it.id != leg.id && transferId != null && it.current.transferId == transferId })
            }
    }

    /**
     * Other messages still waiting that look like the same payment. They are only mentioned: once one is saved, the
     * next is offered it as a possible duplicate.
     */
    fun pendingTwins(draft: Draft, others: List<Draft>): List<Draft> {
        val proposal = proposalOf(draft) ?: return emptyList()
        val amount = proposal.amount ?: return emptyList()
        val date = proposal.date ?: return emptyList()
        return others.filter { other ->
            val twin = proposalOf(other)
            other.id != draft.id && other.status == DraftStatus.PENDING_REVIEW && twin != null && twin.amount == amount &&
                twin.date?.let { daysApart(it, date) <= DUPLICATE_WINDOW_DAYS } == true &&
                (proposal.direction == null || twin.direction == null || proposal.direction == twin.direction)
        }
    }

    /** Two names are the same shop when they share a word that is not just a common one. */
    fun sharesName(a: String?, b: String?): Boolean = words(a).intersect(words(b)).isNotEmpty()

    private fun words(name: String?): Set<String> =
        name.orEmpty().lowercase(Locale.ROOT).split(notAlphanumeric).filter { it.length >= 3 && it !in COMMON_WORDS }.toSet()

    private fun daysApart(a: LocalDate, b: LocalDate): Long = abs(ChronoUnit.DAYS.between(a, b))
}
