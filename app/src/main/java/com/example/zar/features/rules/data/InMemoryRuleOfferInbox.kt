package com.example.zar.features.rules.data

import com.example.zar.features.auth.domain.repository.SignOutHandler
import com.example.zar.features.rules.domain.model.RuleOffer
import com.example.zar.features.rules.domain.repository.RuleOfferInbox
import com.example.zar.features.rules.domain.repository.RuleOfferState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the one pending offer in memory. An offer is never kept across sign-out or a restart. */
@Singleton
class InMemoryRuleOfferInbox @Inject constructor() : RuleOfferInbox, SignOutHandler {
    private val _state = MutableStateFlow(RuleOfferState())
    override val state: StateFlow<RuleOfferState> = _state.asStateFlow()

    override fun post(offer: RuleOffer) {
        _state.value = RuleOfferState(offer = offer, sheetOpen = false)
    }

    override fun open(offer: RuleOffer) {
        _state.value = RuleOfferState(offer = offer, sheetOpen = true)
    }

    override fun dismiss() {
        _state.value = RuleOfferState()
    }

    override suspend fun onSignOut() = dismiss()
}
