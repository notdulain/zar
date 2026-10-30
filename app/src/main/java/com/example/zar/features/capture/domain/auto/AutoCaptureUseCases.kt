package com.example.zar.features.capture.domain.auto

import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.auth.domain.repository.SignOutHandler
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.example.zar.features.capture.domain.usecase.CaptureTextUseCase
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

enum class StageOutcome {
    /** Written encrypted to the phone; a background pass will turn it into a draft. */
    STAGED,
    ALREADY_STAGED,

    /** Capture is off, or was turned on by someone else on this phone. Nothing is stored. */
    OFF,
    SIGNED_OUT,

    /** Not a tested bank payment format (a personal message, an OTP, a notice). Nothing is stored. */
    NOT_A_PAYMENT,
}

/**
 * Called for each newly received SMS. Only messages in a tested bank format, for the user who
 * turned capture on, are kept, and only until the background pass has read them.
 */
class StageIncomingSmsUseCase @Inject constructor(
    private val parser: BankSmsParser,
    private val session: UserSessionProvider,
    private val preferences: AutoCapturePreferences,
    private val staging: SmsStagingRepository,
    private val scheduler: AutoCaptureScheduler,
) {
    suspend operator fun invoke(text: String, receivedAt: Instant): StageOutcome {
        val owner = session.currentUserId() ?: return StageOutcome.SIGNED_OUT
        if (preferences.enabledFor() != owner) return StageOutcome.OFF
        if (!AutoCaptureRules.shouldStage(parser.parse(text))) return StageOutcome.NOT_A_PAYMENT
        val staged = staging.stage(StagedMessage(AutoCaptureRules.stagingIdFor(owner, text), owner, text, receivedAt))
        scheduler.processSoon()
        return if (staged) StageOutcome.STAGED else StageOutcome.ALREADY_STAGED
    }
}

/** How a background pass went. Anything left waiting is retried later until it expires. */
data class ProcessResult(val processed: Int, val left: Int, val expired: Int, val paused: Boolean)

/**
 * Turns the signed-in user's staged messages into pending drafts through the same rules as a
 * shared message, then deletes their text. Another user's messages are never read or uploaded:
 * they stay paused until their owner signs back in, or expire.
 */
class ProcessStagedSmsUseCase @Inject constructor(
    private val session: UserSessionProvider,
    private val staging: SmsStagingRepository,
    private val captureText: CaptureTextUseCase,
) {
    suspend operator fun invoke(now: Instant): ProcessResult {
        val expired = staging.expire(AutoCaptureRules.expiryCutoff(now))
        val owner = session.currentUserId() ?: return ProcessResult(0, 0, expired, paused = true)
        var processed = 0
        var left = 0
        for (message in staging.waiting(owner)) {
            // Signed out or switched user mid-pass: stop, and leave the rest for their owner.
            if (session.currentUserId() != owner) return ProcessResult(processed, left, expired, paused = true)
            // Bound to the owner checked above: a switch after the check cannot redirect the draft to the new user.
            captureText(message.text, DraftSource.SMS, message.receivedAt, owner).fold(
                onSuccess = {
                    staging.clear(message.id)
                    processed++
                },
                onFailure = { left++ },
            )
        }
        return ProcessResult(processed, left, expired, paused = false)
    }
}

data class AutoCaptureStatus(
    /** On for the signed-in user. */
    val enabled: Boolean = false,
    val queue: StagingQueue = StagingQueue(),
)

class ObserveAutoCaptureUseCase @Inject constructor(
    private val session: UserSessionProvider,
    private val preferences: AutoCapturePreferences,
    private val staging: SmsStagingRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<AutoCaptureStatus> = session.observeUserId().flatMapLatest { owner ->
        if (owner == null) {
            flowOf(AutoCaptureStatus())
        } else {
            combine(preferences.observeEnabledFor().map { it == owner }, staging.observe(owner), ::AutoCaptureStatus)
        }
    }
}

/**
 * Turns capture on for the signed-in user, or off. Turning it off deletes their messages still
 * waiting, so nothing is captured after they said stop.
 */
class SetAutoCaptureUseCase @Inject constructor(
    private val session: UserSessionProvider,
    private val preferences: AutoCapturePreferences,
    private val staging: SmsStagingRepository,
    private val scheduler: AutoCaptureScheduler,
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> = attempt {
        val owner = requireNotNull(session.currentUserId()) { "Not signed in" }
        if (enabled) {
            preferences.setEnabledFor(owner)
            scheduler.ensurePeriodic()
        } else {
            preferences.setEnabledFor(null)
            staging.discardWaiting(owner)
            // The regular pass keeps running so anyone else's paused messages still expire.
            scheduler.cancelPending()
        }
    }
}

/**
 * Whenever the user who turned capture on is signed in (at startup, or signing back in after a
 * pause), their waiting messages are processed. Runs for the app's lifetime.
 */
class ResumeAutoCaptureUseCase @Inject constructor(
    private val session: UserSessionProvider,
    private val preferences: AutoCapturePreferences,
    private val scheduler: AutoCaptureScheduler,
) {
    suspend operator fun invoke() {
        combine(session.observeUserId(), preferences.observeEnabledFor()) { owner, enabledFor -> owner != null && owner == enabledFor }
            .distinctUntilChanged()
            .collect { active ->
                if (active) {
                    scheduler.ensurePeriodic()
                    scheduler.processSoon()
                }
            }
    }
}

class DismissExpiredSmsUseCase @Inject constructor(
    private val session: UserSessionProvider,
    private val staging: SmsStagingRepository,
) {
    suspend operator fun invoke(): Result<Unit> = attempt { session.currentUserId()?.let { staging.dismissExpired(it) } }
}

/**
 * Signing out pauses capture: the pending pass is stopped, new messages are no longer staged
 * (nobody is signed in), and waiting ones stay bound to their owner until they sign back in or
 * the messages expire.
 */
class PauseAutoCaptureOnSignOut @Inject constructor(
    private val scheduler: AutoCaptureScheduler,
) : SignOutHandler {
    override suspend fun onSignOut() = scheduler.cancelPending()
}

private inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
