package com.example.zar.features.rules.domain

import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.features.rules.FakeCategoryRules
import com.example.zar.features.rules.domain.model.CategoryRuleId
import com.example.zar.features.rules.domain.model.OfferDecision
import com.example.zar.features.rules.domain.model.RuleScope
import com.example.zar.features.rules.domain.usecase.EditCategoryRuleUseCase
import com.example.zar.features.rules.domain.usecase.ObserveCategoryRulesUseCase
import com.example.zar.features.rules.domain.usecase.RememberCategoryUseCase
import com.example.zar.features.rules.rule
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRuleUseCasesTest {
    private val now = Instant.parse("2026-10-23T04:00:00Z")
    private val repo = FakeCategoryRules()
    private val remember = RememberCategoryUseCase(repo)
    private val edit = EditCategoryRuleUseCase(repo)

    private fun offer(name: String = "Keells Super", category: String = "groceries") =
        (RuleOfferRules.decide(name, category, cash.id, repo.rules.value) as OfferDecision.Offer).offer

    @Test
    fun rememberingSavesOneEnabledRuleAtVersionOne() = runBlocking {
        val saved = remember(offer(), RuleScope.ANY_ACCOUNT, CategoryRuleId("r1"), now).getOrThrow()
        assertEquals(listOf(saved), repo.rules.value)
        assertEquals(1, saved.version)
        assertTrue(saved.enabled)
        assertEquals(null, saved.accountId)
    }

    @Test
    fun aRetryWithTheSameIdDoesNotAddASecondRuleOrRaiseTheVersion() = runBlocking {
        val first = offer()
        remember(first, RuleScope.ANY_ACCOUNT, CategoryRuleId("r1"), now)
        remember(first, RuleScope.ANY_ACCOUNT, CategoryRuleId("r1"), now.plusSeconds(5))
        assertEquals(1, repo.rules.value.size)
        assertEquals(1, repo.saves)
    }

    @Test
    fun aRetryAfterTheChangeToAnExistingRuleLandedDoesNotBumpTheVersionAgain() = runBlocking {
        repo.rules.value = listOf(rule("Keells Super", category = "groceries"))
        val change = offer(category = "coffee-dining")
        remember(change, RuleScope.ANY_ACCOUNT, CategoryRuleId("unused"), now)
        remember(change, RuleScope.ANY_ACCOUNT, CategoryRuleId("unused"), now.plusSeconds(5))
        val only = repo.rules.value.single()
        assertEquals("coffee-dining", only.categoryId)
        assertEquals(2, only.version)
    }

    @Test
    fun aSavingFailureIsReportedAndNothingIsKept() = runBlocking {
        repo.saveError = IOException("denied")
        val result = remember(offer(), RuleScope.ANY_ACCOUNT, CategoryRuleId("r1"), now)
        assertTrue(result.isFailure)
        assertTrue(repo.rules.value.isEmpty())
    }

    @Test
    fun rememberingForThisAccountKeepsTheAnyAccountRuleToo() = runBlocking {
        repo.rules.value = listOf(rule("Keells Super", category = "groceries", id = "any"))
        val change = offer(category = "coffee-dining")
        remember(change, RuleScope.THIS_ACCOUNT, CategoryRuleId("cash-rule"), now)
        assertEquals(setOf("any", "cash-rule"), repo.rules.value.map { it.id.value }.toSet())
        assertEquals(cash.id, repo.rules.value.first { it.id.value == "cash-rule" }.accountId)
        assertEquals(1, repo.rules.value.first { it.id.value == "any" }.version)
    }

    @Test
    fun editingChangesTheRuleAndRaisesTheVersionOnce() = runBlocking {
        repo.rules.value = listOf(rule("Keells Super", version = 1))
        val edited = edit(CategoryRuleId("r-keells-super"), "coffee-dining", bank.id, true, now).getOrThrow()
        assertEquals("coffee-dining", edited.categoryId)
        assertEquals(bank.id, edited.accountId)
        assertEquals(2, edited.version)
        assertEquals("Keells Super", edited.displayName)
    }

    @Test
    fun savingAnEditorWithNothingChangedKeepsTheVersionAndWritesNothing() = runBlocking {
        repo.rules.value = listOf(rule("Keells Super", version = 3))
        edit(CategoryRuleId("r-keells-super"), "groceries", null, true, now).getOrThrow()
        assertEquals(0, repo.saves)
        assertEquals(3, repo.rules.value.single().version)
    }

    @Test
    fun switchingARuleOffKeepsItAndItIsNeverDeleted() = runBlocking {
        repo.rules.value = listOf(rule("Keells Super"))
        edit(CategoryRuleId("r-keells-super"), "groceries", null, false, now)
        val only = repo.rules.value.single()
        assertFalse(only.enabled)
        assertEquals(2, only.version)
    }

    @Test
    fun editingARuleThatIsGoneFails() = runBlocking {
        val result = edit(CategoryRuleId("missing"), "groceries", null, true, now)
        assertTrue(result.exceptionOrNull() is LedgerError.InvalidRecord)
    }

    @Test
    fun theListShowsEnabledRulesFirstThenByName() = runBlocking {
        repo.rules.value = listOf(
            rule("Pilawoos kottu", category = "coffee-dining", enabled = false),
            rule("Keells Super"),
            rule("Dialog", category = "bills"),
        )
        val names = ObserveCategoryRulesUseCase(repo)().first().map { it.displayName }
        assertEquals(listOf("Dialog", "Keells Super", "Pilawoos kottu"), names)
    }
}
