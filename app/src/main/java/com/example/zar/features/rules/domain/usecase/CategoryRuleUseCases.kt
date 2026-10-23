package com.example.zar.features.rules.domain.usecase

import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.features.rules.domain.RuleOfferRules
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.model.CategoryRuleId
import com.example.zar.features.rules.domain.model.RuleOffer
import com.example.zar.features.rules.domain.model.RuleScope
import com.example.zar.features.rules.domain.repository.CategoryRuleRepository
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ObserveCategoryRulesUseCase @Inject constructor(
    private val rules: CategoryRuleRepository,
) {
    /** Enabled rules first, then by name. */
    operator fun invoke(): Flow<List<CategoryRule>> = rules.observeRules().map { list ->
        list.sortedWith(compareByDescending<CategoryRule> { it.enabled }.thenBy { it.displayName.lowercase() })
    }
}

/**
 * Saves a rule for an offer. Reuse the same [newId] when retrying; a retry after the rule was already
 * saved changes nothing.
 */
class RememberCategoryUseCase @Inject constructor(
    private val rules: CategoryRuleRepository,
) {
    suspend operator fun invoke(offer: RuleOffer, scope: RuleScope, newId: CategoryRuleId, now: Instant): Result<CategoryRule> =
        attempt {
            val current = rules.observeRules().first()
            current.firstOrNull { it.id == newId }?.let { return@attempt it }
            val existing = RuleOfferRules.ruleToChange(offer, scope, current)
            // A rule for this name already has the category (an earlier attempt landed): nothing to add.
            if (existing != null && existing.enabled && existing.categoryId == offer.categoryId) {
                return@attempt existing
            }
            val rule = RuleOfferRules.remember(offer, scope, newId, now, existing)
            rules.saveRule(rule)
            rule
        }
}

/** Changes a rule's category, account or on/off state. The version only goes up when something changed. */
class EditCategoryRuleUseCase @Inject constructor(
    private val rules: CategoryRuleRepository,
) {
    suspend operator fun invoke(
        ruleId: CategoryRuleId,
        categoryId: String,
        accountId: AccountId?,
        enabled: Boolean,
        now: Instant,
    ): Result<CategoryRule> = attempt {
        val current = rules.observeRules().first().firstOrNull { it.id == ruleId }
            ?: throw LedgerError.InvalidRecord("That rule no longer exists")
        val edited = RuleOfferRules.edit(current, categoryId, accountId, enabled, now)
        if (edited != current) rules.saveRule(edited)
        edited
    }
}

private inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
