package com.example.zar.features.rules.domain

import com.example.zar.core.domain.model.AccountId
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.model.RuleMatch

object CategoryRuleMatcher {
    /**
     * The rule that files an expense named [merchant] on [accountId], or why none does.
     * A rule limited to this account outranks one for every account. Rules of the same weight that
     * disagree on the category are a conflict and file nothing.
     */
    fun match(rules: List<CategoryRule>, merchant: String?, accountId: AccountId?): RuleMatch {
        val key = MerchantKey.of(merchant) ?: return RuleMatch.NoRule
        if (MerchantKey.isGeneric(key)) return RuleMatch.TooGeneral
        val eligible = rules.filter { rule ->
            rule.enabled && rule.merchantKey == key && (rule.accountId == null || rule.accountId == accountId)
        }
        if (eligible.isEmpty()) return RuleMatch.NoRule
        val sameWeight = eligible.filter { it.accountId != null }.ifEmpty { eligible }
        if (sameWeight.map { it.categoryId }.distinct().size > 1) return RuleMatch.Conflict(sameWeight)
        return RuleMatch.Applied(sameWeight.maxWith(compareBy<CategoryRule> { it.version }.thenBy { it.updatedAt }))
    }
}
