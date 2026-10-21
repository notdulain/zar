package com.example.zar.features.quicknote.data.repository

import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.quicknote.data.mapper.QuickNoteDraftMapper
import com.example.zar.features.quicknote.data.remote.QuickNoteDraftTransport
import com.example.zar.features.quicknote.data.remote.QuickNotePayload
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftRequest
import com.example.zar.features.quicknote.domain.model.QuickNoteLimits
import com.example.zar.features.quicknote.domain.repository.QuickNoteDraftRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/**
 * Generates a draft through the authenticated service and maps it onto the shared draft contract.
 * The draft gets a fresh stable [DraftId] and `PENDING_REVIEW`. It is returned to the caller only: this class
 * writes nothing, keeps no copy, and does not log. The eight-second budget covers the whole service call; hitting
 * it is a [QuickNoteDraftError.TimedOut], while the caller's own cancellation propagates unchanged.
 */
@Singleton
class CallableQuickNoteDraftRepository internal constructor(
    private val transport: QuickNoteDraftTransport,
    private val session: UserSessionProvider,
    private val newId: () -> String,
    private val now: () -> Instant,
    private val budgetMillis: Long,
) : QuickNoteDraftRepository {

    @Inject
    constructor(transport: QuickNoteDraftTransport, session: UserSessionProvider) : this(
        transport, session, RecordIds::newId, Instant::now, QuickNoteLimits.GENERATION_BUDGET_MILLIS,
    )

    override suspend fun generate(request: QuickNoteDraftRequest): Draft {
        if (session.currentUserId() == null) throw QuickNoteDraftError.NotSignedIn()
        val raw = try {
            withTimeout(budgetMillis) { transport.createDraft(QuickNotePayload.from(request)) }
        } catch (_: TimeoutCancellationException) {
            // A caller's shorter deadline must still cancel this operation.
            currentCoroutineContext().ensureActive()
            throw QuickNoteDraftError.TimedOut()
        }
        return QuickNoteDraftMapper.toDraft(raw, request, DraftId(newId()), now())
    }
}
