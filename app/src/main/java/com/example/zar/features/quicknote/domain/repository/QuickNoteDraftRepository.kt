package com.example.zar.features.quicknote.domain.repository

import com.example.zar.core.domain.model.Draft
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftRequest

/**
 * Turns a typed note into a pending [Draft]. This is generation only: it never saves a draft and never posts
 * a record. Saving is the review flow's job (`DraftRepository`, S07).
 */
interface QuickNoteDraftRepository {
    /**
     * @throws QuickNoteDraftError for every expected failure. Coroutine cancellation is propagated untouched.
     */
    suspend fun generate(request: QuickNoteDraftRequest): Draft
}
