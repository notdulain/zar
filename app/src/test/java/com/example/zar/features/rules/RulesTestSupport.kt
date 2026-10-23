package com.example.zar.features.rules

import com.example.zar.core.domain.model.AccountId
import com.example.zar.features.rules.data.InMemoryRuleOfferInbox
import com.example.zar.features.rules.domain.MerchantKey
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.model.CategoryRuleId
import com.example.zar.features.rules.domain.repository.CategoryRuleRepository
import com.example.zar.features.rules.domain.usecase.ObserveCategoryRulesUseCase
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Rules kept in memory. Like Firestore, saving a rule by an ID that exists replaces it. */
class FakeCategoryRules(initial: List<CategoryRule> = emptyList()) : CategoryRuleRepository {
    val rules = MutableStateFlow(initial)
    var saveError: Throwable? = null
    var saves = 0

    override fun observeRules(): Flow<List<CategoryRule>> = rules

    override suspend fun saveRule(rule: CategoryRule) {
        saveError?.let { throw it }
        saves++
        rules.value = rules.value.filterNot { it.id == rule.id } + rule
    }

    fun observeUseCase() = ObserveCategoryRulesUseCase(this)
}

val T0: Instant = Instant.parse("2026-10-20T04:00:00Z")

fun rule(
    name: String = "Keells Super",
    category: String = "groceries",
    id: String = "r-${name.lowercase().replace(' ', '-')}",
    account: AccountId? = null,
    enabled: Boolean = true,
    version: Int = 1,
    updatedAt: Instant = T0,
) = CategoryRule(
    id = CategoryRuleId(id),
    merchantKey = requireNotNull(MerchantKey.of(name)),
    displayName = name,
    categoryId = category,
    accountId = account,
    enabled = enabled,
    version = version,
    createdAt = T0,
    updatedAt = updatedAt,
)

fun newInbox() = InMemoryRuleOfferInbox()
