package com.example.zar.features.quicknote.domain.model

import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.TransactionType

/**
 * A generated refund cannot be saved until the user picks the purchase it returns money for: the model cannot
 * know that, and the shared draft contract has no field for it. The review flow must collect `refundOf`.
 */
val DraftProposal.requiresPurchaseSelection: Boolean
    get() = this is DraftProposal.ActualTransaction && type == TransactionType.REFUND
