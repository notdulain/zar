package com.example.zar.core.domain.repository

import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftStatus
import kotlinx.coroutines.flow.Flow

/** Unconfirmed proposals awaiting review. Drafts never touch balances or totals. */
interface DraftRepository {
    fun observePendingDrafts(): Flow<List<Draft>>

    /** Every draft whatever its status, e.g. to show when something was last captured. */
    fun observeDrafts(): Flow<List<Draft>>
    suspend fun saveDraft(draft: Draft)
    suspend fun setStatus(id: DraftId, status: DraftStatus)
}
