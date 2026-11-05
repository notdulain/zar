package com.example.zar.features.capture.domain.auto

import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.UserId
import com.example.zar.features.capture.domain.model.SmsParseResult
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * A newly received bank SMS waiting on this phone to become a draft. Bound to the user who
 * turned capture on; [text] is only ever held encrypted at rest and is deleted once processed.
 */
data class StagedMessage(
    val id: String,
    val owner: UserId,
    val text: String,
    val receivedAt: Instant,
)

/** What is still on the phone for one user: messages waiting, and ones that ran out of time. */
data class StagingQueue(
    val waiting: Int = 0,
    /** When each expired message arrived. Its text is already gone; only this notice is kept. */
    val expired: List<Instant> = emptyList(),
)

/** Encrypted local staging. Implementations never store message text in plain form. */
interface SmsStagingRepository {
    /** False when this message was already staged, so a repeated broadcast adds nothing. */
    suspend fun stage(message: StagedMessage): Boolean

    suspend fun waiting(owner: UserId): List<StagedMessage>

    /** Deletes the message text once it has been turned into a draft or found to need nothing. */
    suspend fun clear(id: String)

    /** Deletes the text of anyone's messages received before [cutoff], keeping an expiry notice. Returns how many. */
    suspend fun expire(cutoff: Instant): Int

    /** Deletes every waiting message of [owner], e.g. when they switch capture off. */
    suspend fun discardWaiting(owner: UserId)

    suspend fun dismissExpired(owner: UserId)

    fun observe(owner: UserId): Flow<StagingQueue>
}

/** Who turned automatic capture on, on this phone. Null means off. */
interface AutoCapturePreferences {
    fun observeEnabledFor(): Flow<UserId?>
    suspend fun enabledFor(): UserId?
    suspend fun setEnabledFor(owner: UserId?)
}

/** Runs [ProcessStagedSmsUseCase] in the background. */
interface AutoCaptureScheduler {
    /** Process soon, once there is a connection. */
    fun processSoon()

    /** A regular pass, so leftovers are retried and old messages expire even if nothing new arrives. */
    fun ensurePeriodic()

    /** Stops the pending pass, e.g. on sign-out. The regular pass keeps expiring old messages. */
    fun cancelPending()
}

/** A captured message waiting for review, as the phone's notification describes it. Never the message text. */
data class CapturedForReview(
    val draftId: DraftId,
    val amount: FiatMoney,
    val counterparty: String?,
    /** The account matched from the message, if one was. */
    val accountName: String?,
)

/** Tells the person a message was captured in the background and needs their review. */
fun interface CaptureNotifier {
    fun draftWaiting(capture: CapturedForReview)
}

object AutoCaptureRules {
    /** Messages that couldn't be saved within this time are deleted, with a notice. */
    val EXPIRY: Duration = Duration.ofDays(7)

    /** Only messages in a tested bank format are ever written to the phone. */
    fun shouldStage(result: SmsParseResult): Boolean = result is SmsParseResult.Parsed

    /** The same message for the same user always gets the same ID, so it is staged once. */
    fun stagingIdFor(owner: UserId, text: String): String {
        val normalized = text.replace(Regex("""\s+"""), " ").trim()
        val digest = MessageDigest.getInstance("SHA-256").digest("${owner.value}|$normalized".toByteArray(Charsets.UTF_8))
        return "sms-" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    fun expiryCutoff(now: Instant): Instant = now.minus(EXPIRY)
}
