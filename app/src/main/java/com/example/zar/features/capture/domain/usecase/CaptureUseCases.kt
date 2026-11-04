package com.example.zar.features.capture.domain.usecase

import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.EvidenceId
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.DraftRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.repository.LinkWrite
import com.example.zar.features.capture.domain.CapturedDraftRules
import com.example.zar.features.capture.domain.SmsAccountMatcher
import com.example.zar.features.capture.domain.model.ParsedBankSms
import com.example.zar.features.capture.domain.model.SmsIgnoreReason
import com.example.zar.features.capture.domain.model.SmsParseResult
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.example.zar.features.rules.domain.CategoryRuleMatcher
import com.example.zar.features.rules.domain.model.RuleMatch
import com.example.zar.features.rules.domain.usecase.ObserveCategoryRulesUseCase
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/** Drafts that came from bank messages (shared, pasted or, later, SMS). */
private val CAPTURE_SOURCES = setOf(DraftSource.SHARED_TEXT, DraftSource.SMS)

sealed interface CaptureOutcome {
    /**
     * Read and saved as a pending draft for review. [account] is the one matched from the message, if any; [isNew]
     * is false when the same message was already waiting, e.g. shared before the SMS was read.
     */
    data class Drafted(val draftId: DraftId, val parsed: ParsedBankSms, val account: Account? = null, val isNew: Boolean = true) : CaptureOutcome

    /** An OTP, a declined payment or a notice: nothing to record. */
    data class Ignored(val reason: SmsIgnoreReason) : CaptureOutcome

    /** Mentions money but isn't a tested format. Nothing is guessed; add it manually. */
    data object Unrecognised : CaptureOutcome

    data object AlreadyReviewed : CaptureOutcome

    data object Empty : CaptureOutcome
}

/**
 * Reads shared or pasted bank text on the device and saves only the structured fields as a
 * pending draft. The text itself is dropped once read.
 */
class CaptureTextUseCase @Inject constructor(
    private val parser: BankSmsParser,
    private val drafts: DraftRepository,
    private val accounts: ObserveCaptureAccountsUseCase,
) {
    /** With [owner] the draft is saved only if that user is still signed in (see [DraftRepository.saveDraftFor]). */
    suspend operator fun invoke(text: String, source: DraftSource, now: Instant, owner: UserId? = null): Result<CaptureOutcome> = attempt {
        if (text.isBlank()) return@attempt CaptureOutcome.Empty
        when (val result = parser.parse(text)) {
            is SmsParseResult.Ignored -> CaptureOutcome.Ignored(result.reason)
            SmsParseResult.Unrecognised -> CaptureOutcome.Unrecognised
            is SmsParseResult.Parsed -> {
                val id = CapturedDraftRules.draftIdFor(text)
                // Accounts that cannot be read in a moment only mean no suggestion: capturing never waits on them.
                val known = runCatching { withTimeoutOrNull(ACCOUNTS_WAIT_MS) { accounts().first() } }.getOrNull().orEmpty()
                val suggested = SmsAccountMatcher.match(result.sms.format, result.sms.accountHint, result.sms.amount.currency, known)
                val draft = CapturedDraftRules.draftFrom(id, result.sms, source, now, suggested)
                val saved = if (owner == null) drafts.saveDraft(draft) else drafts.saveDraftFor(owner, draft)
                if (saved.status == DraftStatus.PENDING_REVIEW) CaptureOutcome.Drafted(
                    draftId = id,
                    parsed = result.sms,
                    account = known.firstOrNull { it.id == suggested },
                    // A replay returns the draft as first saved, with its own source and capture time.
                    isNew = saved.source == draft.source && saved.createdAt == draft.createdAt,
                )
                else CaptureOutcome.AlreadyReviewed
            }
        }
    }
}

/** Captured drafts still waiting for the user, newest first. */
class ObserveCapturedDraftsUseCase @Inject constructor(
    private val drafts: DraftRepository,
) {
    operator fun invoke(): Flow<List<Draft>> = drafts.observePendingDrafts().map { list ->
        list.filter { it.source in CAPTURE_SOURCES }.sortedByDescending { it.createdAt }
    }
}

/**
 * When something was last captured. Silence is never treated as "nothing happened": Zar only
 * knows about messages it was given.
 */
data class CaptureHealth(val lastCapturedAt: Instant?, val lastSource: DraftSource?, val waitingForReview: Int)

class ObserveCaptureHealthUseCase @Inject constructor(
    private val drafts: DraftRepository,
) {
    operator fun invoke(): Flow<CaptureHealth> = drafts.observeDrafts().map { list ->
        val captured = list.filter { it.source in CAPTURE_SOURCES }
        val latest = captured.maxByOrNull { it.createdAt }
        CaptureHealth(latest?.createdAt, latest?.source, captured.count { it.status == DraftStatus.PENDING_REVIEW })
    }
}

class ObserveCaptureAccountsUseCase @Inject constructor(
    private val accounts: AccountRepository,
) {
    operator fun invoke(): Flow<List<Account>> = accounts.observeAccounts().map { list -> list.filterNot { it.isArchived } }
}

/** Saves the reviewed record, then marks the draft confirmed. Confirming twice saves once. */
class ConfirmCapturedDraftUseCase @Inject constructor(
    private val drafts: DraftRepository,
    private val rules: ObserveCategoryRulesUseCase,
) {
    suspend operator fun invoke(
        draft: Draft,
        account: Account,
        type: TransactionType,
        spendingClass: SpendingClass?,
        now: Instant,
    ): Result<Unit> {
        val transaction = runCatching {
            // A remembered name files the expense; a general name or disagreeing rules leave it for the person.
            val merchant = (draft.proposals.singleOrNull() as? DraftProposal.ActualTransaction)?.merchant
            val rule = if (type == TransactionType.EXPENSE) {
                // Rules that cannot be read in a moment are skipped: confirming never waits on them.
                val saved = runCatching { withTimeoutOrNull(RULES_WAIT_MS) { rules().first() } }.getOrNull().orEmpty()
                (CategoryRuleMatcher.match(saved, merchant, account.id) as? RuleMatch.Applied)?.rule
            } else {
                null
            }
            CapturedDraftRules.transactionFrom(draft, account, type, spendingClass, now, rule)
        }
            .getOrElse { return Result.failure(it) }
        return attempt { drafts.confirmCapturedDraft(draft, transaction) }
    }
}

/** What a message is compared against in review: the entries still standing. Undone entries are left out. */
class ObserveCaptureLedgerUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    operator fun invoke(): Flow<List<LedgerEntry>> = ledger.observeLedger().map { it.active() }
}

/**
 * Attaches a message to the entry it reports and closes its draft, posting no money. The message is the entry's
 * evidence, so the same payment seen through two sources still counts once. Repeating it changes nothing.
 */
class LinkCapturedDraftUseCase @Inject constructor(
    private val drafts: DraftRepository,
    private val ledger: LedgerRepository,
) {
    fun observeWrite(id: DraftId, target: TransactionId): Flow<LinkWrite> = drafts.observeLinkWrite(id, target)

    suspend operator fun invoke(draft: Draft, target: TransactionId, now: Instant): Result<LinkWrite> = attempt {
        require(draft.status == DraftStatus.PENDING_REVIEW) { "Draft already reviewed" }
        require(ledger.observeLedger().first().active().any { it.id == target }) { "That entry no longer exists" }
        drafts.closeAsLinked(draft, EvidenceLink(EvidenceId(draft.id.value), target, now))
    }
}

class DiscardCapturedDraftUseCase @Inject constructor(
    private val drafts: DraftRepository,
) {
    suspend operator fun invoke(draft: Draft): Result<Unit> = attempt { drafts.setStatus(draft.id, DraftStatus.DISCARDED) }
}

private const val RULES_WAIT_MS = 2_000L
private const val ACCOUNTS_WAIT_MS = 2_000L

private inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
