package com.example.zar.features.rules.domain

import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.model.NotOfferedReason
import com.example.zar.features.rules.domain.model.OfferDecision
import com.example.zar.features.rules.domain.model.RuleOffer

/** What an entry's page says about category rules (Figma Z14, Z119, Z122). */
sealed interface RuleCard {
    data object Hidden : RuleCard

    /** "Always file Keells Super as Groceries?" */
    data class Offer(val offer: RuleOffer) : RuleCard

    /** The name is too general to remember, so no offer is made. */
    data class TooGeneral(val name: String) : RuleCard

    /** A rule filled in the category and it still stands. [version] is the one that filed it. */
    data class Applied(val name: String, val categoryId: String, val version: Int) : RuleCard
}

object RuleCardRules {
    /**
     * The card for [entry]. Only a live expense gets one. An expense a rule filed shows that rule and its
     * version while the category is still the rule's; once a correction changes the category, the entry
     * is treated like any other and may be offered a new rule.
     */
    fun of(entry: LedgerEntry, rules: List<CategoryRule>): RuleCard {
        val original = entry.original
        val current = entry.current
        if (entry.isVoided || current.type != TransactionType.EXPENSE) return RuleCard.Hidden
        val ruleId = original.appliedRuleId
        val version = original.appliedRuleVersion
        val categoryId = current.categoryId
        if (ruleId != null && version != null && categoryId != null && categoryId == original.categoryId) {
            val name = rules.firstOrNull { it.id.value == ruleId }?.displayName ?: current.merchant.orEmpty()
            return RuleCard.Applied(name, categoryId, version)
        }
        return when (val decision = RuleOfferRules.decide(current.merchant, categoryId, current.accountId, rules)) {
            is OfferDecision.Offer -> RuleCard.Offer(decision.offer)
            is OfferDecision.NotOffered ->
                if (decision.reason == NotOfferedReason.TOO_GENERAL) RuleCard.TooGeneral(current.merchant.orEmpty().trim()) else RuleCard.Hidden
        }
    }
}
