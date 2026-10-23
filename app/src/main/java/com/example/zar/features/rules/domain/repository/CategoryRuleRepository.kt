package com.example.zar.features.rules.domain.repository

import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.model.RuleOffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The signed-in user's category rules. A rule is saved by its stable ID, so saving it again (a retry
 * or an offline replay) cannot add a second one. Saving works offline; sync happens later.
 * All calls throw LedgerError.NotSignedIn when nobody is signed in.
 */
interface CategoryRuleRepository {
    fun observeRules(): Flow<List<CategoryRule>>

    suspend fun saveRule(rule: CategoryRule)
}

data class RuleOfferState(val offer: RuleOffer? = null, val sheetOpen: Boolean = false)

/** The offer waiting to be answered, shared between the screen that saved an expense and the screen shown next. */
interface RuleOfferInbox {
    val state: StateFlow<RuleOfferState>

    /** Shows the small "Remember" card. */
    fun post(offer: RuleOffer)

    /** Opens the confirm sheet straight away (from an entry's detail). */
    fun open(offer: RuleOffer)

    fun dismiss()
}
