package com.example.zar.features.capture.domain.usecase

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.DraftRepository
import com.example.zar.features.capture.domain.CapturedDraftRules
import com.example.zar.features.capture.domain.model.ParsedBankSms
import com.example.zar.features.capture.domain.model.SmsIgnoreReason
import com.example.zar.features.capture.domain.model.SmsParseResult
import com.example.zar.features.capture.domain.parser.BankSmsParser
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Drafts that came from bank messages (shared, pasted or, later, SMS). */
private val CAPTURE_SOURCES = setOf(DraftSource.SHARED_TEXT, DraftSource.SMS)

sealed interface CaptureOutcome {
    /** Read and saved as a pending draft for review. */
    data class Drafted(val draftId: DraftId, val parsed: ParsedBankSms) : CaptureOutcome

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
) {
    suspend operator fun invoke(text: String, source: DraftSource, now: Instant): Result<CaptureOutcome> = attempt {
        if (text.isBlank()) return@attempt CaptureOutcome.Empty
        when (val result = parser.parse(text)) {
            is SmsParseResult.Ignored -> CaptureOutcome.Ignored(result.reason)
            SmsParseResult.Unrecognised -> CaptureOutcome.Unrecognised
            is SmsParseResult.Parsed -> {
                val id = CapturedDraftRules.draftIdFor(text)
                val saved = drafts.saveDraft(CapturedDraftRules.draftFrom(id, result.sms, source, now))
                if (saved.status == DraftStatus.PENDING_REVIEW) CaptureOutcome.Drafted(id, result.sms)
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
) {
    suspend operator fun invoke(
        draft: Draft,
        account: Account,
        type: TransactionType,
        spendingClass: SpendingClass?,
        now: Instant,
    ): Result<Unit> {
        val transaction = runCatching { CapturedDraftRules.transactionFrom(draft, account, type, spendingClass, now) }
            .getOrElse { return Result.failure(it) }
        return attempt { drafts.confirmCapturedDraft(draft, transaction) }
    }
}

class DiscardCapturedDraftUseCase @Inject constructor(
    private val drafts: DraftRepository,
) {
    suspend operator fun invoke(draft: Draft): Result<Unit> = attempt { drafts.setStatus(draft.id, DraftStatus.DISCARDED) }
}

private inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
