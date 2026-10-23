package com.example.zar.features.rules.domain.model

import com.example.zar.core.domain.model.AccountId
import java.time.Instant

@JvmInline value class CategoryRuleId(val value: String)

/**
 * "File this exact name under this category from now on." It only ever fills in a category for a
 * future expense; it never changes an amount, a saved entry, the spending type or recurrence.
 * [version] goes up on every edit, and an expense remembers the version that filed it.
 */
data class CategoryRule(
    val id: CategoryRuleId,
    /** The normalised name this rule matches exactly (see MerchantKey). */
    val merchantKey: String,
    /** The name as the person first wrote it, for showing. */
    val displayName: String,
    val categoryId: String,
    /** Null means every account. */
    val accountId: AccountId?,
    val enabled: Boolean,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(merchantKey.isNotBlank()) { "A rule needs a name to match" }
        require(version >= 1) { "Version starts at 1" }
    }
}

/** What the person is asked to remember: this name, this category, and whether it already has a rule. */
data class RuleOffer(
    val displayName: String,
    val merchantKey: String,
    val categoryId: String,
    /** The account of the entry the offer came from. */
    val accountId: AccountId,
    /** A rule for this name without an account that would be updated instead of adding a second one. */
    val existing: CategoryRule?,
)

enum class RuleScope { ANY_ACCOUNT, THIS_ACCOUNT }

/** The outcome of looking for a rule for one expense. */
sealed interface RuleMatch {
    data class Applied(val rule: CategoryRule) : RuleMatch

    data object NoRule : RuleMatch

    /** The name is too general to mean one thing (PickMe, Uber). Never filed automatically. */
    data object TooGeneral : RuleMatch

    /** Several enabled rules of the same weight disagree. Nothing is filed. */
    data class Conflict(val rules: List<CategoryRule>) : RuleMatch
}

enum class NotOfferedReason { NO_NAME, TOO_GENERAL, NO_CATEGORY, ALREADY_REMEMBERED }

sealed interface OfferDecision {
    data class Offer(val offer: RuleOffer) : OfferDecision

    data class NotOffered(val reason: NotOfferedReason) : OfferDecision
}
