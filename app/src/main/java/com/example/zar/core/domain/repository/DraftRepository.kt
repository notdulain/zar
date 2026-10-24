package com.example.zar.core.domain.repository

import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import kotlinx.coroutines.flow.Flow

/** Whether a closing write reached the server, or is stored on this phone and will sync when it can. */
enum class LinkWrite { SAVED, QUEUED }

/** Unconfirmed proposals awaiting review. Drafts never touch balances or totals. */
interface DraftRepository {
    fun observePendingDrafts(): Flow<List<Draft>>

    /** Every draft whatever its status, e.g. to show when something was last captured. */
    fun observeDrafts(): Flow<List<Draft>>
    /** Returns the existing draft on replay without resetting its status or capture time. */
    suspend fun saveDraft(draft: Draft): Draft

    /** Creates the record and closes its draft atomically; a confirmed replay never rewrites the record. */
    suspend fun confirmCapturedDraft(draft: Draft, transaction: Transaction)
    suspend fun setStatus(id: DraftId, status: DraftStatus)

    /**
     * Attaches the message to an entry that already exists and closes its draft, in one write. No money is
     * posted. Repeating the same link is a success; it works offline and syncs later ([LinkWrite.QUEUED]).
     */
    suspend fun closeAsLinked(draft: Draft, link: EvidenceLink): LinkWrite

    /** Follows a queued link until acknowledgement; a rejected write fails the flow. */
    fun observeLinkWrite(id: DraftId, target: TransactionId): Flow<LinkWrite>
}
