package com.example.zar.features.rules.domain

import com.example.zar.core.domain.model.AccountId
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.model.CategoryRuleId
import com.example.zar.features.rules.domain.model.NotOfferedReason
import com.example.zar.features.rules.domain.model.OfferDecision
import com.example.zar.features.rules.domain.model.RuleOffer
import com.example.zar.features.rules.domain.model.RuleScope
import java.time.Instant

object RuleOfferRules {
    private val spaces = Regex("""\s+""")

    /**
     * Whether to offer "Remember this choice" for an expense. Nothing is offered for a blank or
     * too general name, an entry with no category, or a choice an enabled rule already covers.
     */
    fun decide(merchant: String?, categoryId: String?, accountId: AccountId, rules: List<CategoryRule>): OfferDecision {
        val key = MerchantKey.of(merchant) ?: return OfferDecision.NotOffered(NotOfferedReason.NO_NAME)
        if (MerchantKey.isGeneric(key)) return OfferDecision.NotOffered(NotOfferedReason.TOO_GENERAL)
        if (categoryId.isNullOrBlank()) return OfferDecision.NotOffered(NotOfferedReason.NO_CATEGORY)
        val sameName = rules.filter { it.merchantKey == key }
        val covered = sameName.any {
            it.enabled && it.categoryId == categoryId && (it.accountId == null || it.accountId == accountId)
        }
        if (covered) return OfferDecision.NotOffered(NotOfferedReason.ALREADY_REMEMBERED)
        return OfferDecision.Offer(
            RuleOffer(
                displayName = merchant.orEmpty().trim().replace(spaces, " "),
                merchantKey = key,
                categoryId = categoryId,
                accountId = accountId,
                existing = sameName.firstOrNull { it.accountId == null },
            ),
        )
    }

    /**
     * The saved rule that remembering [offer] with [scope] changes: the one for the same name and the same
     * account (or every account). Changing it, instead of adding a second rule of the same weight, keeps the
     * two from disagreeing and filing nothing.
     */
    fun ruleToChange(offer: RuleOffer, scope: RuleScope, rules: List<CategoryRule>): CategoryRule? {
        val accountId = if (scope == RuleScope.THIS_ACCOUNT) offer.accountId else null
        return rules.firstOrNull { it.merchantKey == offer.merchantKey && it.accountId == accountId }
    }

    /**
     * The rule saved when the person agrees. When [existing] is the rule for this name and scope, it is
     * changed (version + 1) instead of adding a second one that would disagree.
     */
    fun remember(
        offer: RuleOffer,
        scope: RuleScope,
        newId: CategoryRuleId,
        now: Instant,
        existing: CategoryRule? = if (scope == RuleScope.ANY_ACCOUNT) offer.existing else null,
    ): CategoryRule {
        return if (existing != null) {
            existing.copy(categoryId = offer.categoryId, enabled = true, version = existing.version + 1, updatedAt = now)
        } else {
            CategoryRule(
                id = newId,
                merchantKey = offer.merchantKey,
                displayName = offer.displayName,
                categoryId = offer.categoryId,
                accountId = if (scope == RuleScope.THIS_ACCOUNT) offer.accountId else null,
                enabled = true,
                version = 1,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    /** The rule after an edit, or the same rule when nothing changed (so the version is not raised for nothing). */
    fun edit(rule: CategoryRule, categoryId: String, accountId: AccountId?, enabled: Boolean, now: Instant): CategoryRule {
        if (rule.categoryId == categoryId && rule.accountId == accountId && rule.enabled == enabled) return rule
        return rule.copy(categoryId = categoryId, accountId = accountId, enabled = enabled, version = rule.version + 1, updatedAt = now)
    }
}
