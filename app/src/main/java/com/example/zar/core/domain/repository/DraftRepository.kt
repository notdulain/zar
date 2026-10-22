package com.example.zar.core.domain.repository

import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

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
}
