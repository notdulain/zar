package com.example.zar.features.transactions.domain.category

import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A duplicate custom name can be created offline on two devices. Reconciliation keeps the earliest as canonical and
 * never rewrites or deletes anything, so every stored ID must keep resolving in selection, search, detail and
 * suggestions. These tests pin that guarantee and the deterministic conflict summary the UI can offer to recover.
 */
class ExpenseCategoryConflictResolutionTest {

    private fun custom(id: String, name: String, at: Long = 1_000) =
        CustomExpenseCategory(id, name, CategoryNameRules.keyOf(name), Instant.ofEpochMilli(at))

    @Test
    fun aDuplicateAliasResolvesInSelectionSearchAndDetail() {
        val canonical = custom("custom-b", "Pets", at = 1_000)
        val duplicate = custom("custom-a", "pets ", at = 5_000)
        val catalog = ExpenseCategoryCatalog(listOf(canonical, duplicate))

        // Selection (find/canonicalId), search (searchTerms) and detail (label) all resolve the duplicate's own ID.
        assertEquals(canonical, catalog.find("custom-a"))
        assertEquals("custom-b", catalog.canonicalId("custom-a"))
        assertEquals(CategoryLabel.Custom("Pets"), catalog.label("custom-a"))
        assertEquals(listOf("Pets"), catalog.searchTerms("custom-a"))
        // Nothing is rewritten or dropped: both IDs stay stored and both keep resolving.
        assertEquals(2, catalog.stored.size)
        assertEquals(CategoryConflict(canonical, listOf("custom-a")), catalog.conflicts.single())
    }

    @Test
    fun aConflictIsDeterministicAndOrderedRegardlessOfStoredOrder() {
        val first = custom("custom-b", "Pets", at = 1_000)
        val sameTime = custom("custom-c", "PETS", at = 1_000)
        val late = custom("custom-a", "pets", at = 5_000)
        val one = ExpenseCategoryCatalog(listOf(late, sameTime, first))
        val two = ExpenseCategoryCatalog(listOf(first, late, sameTime))
        assertEquals(one.conflicts, two.conflicts)
        assertEquals(CategoryConflict(first, listOf("custom-c", "custom-a")), one.conflicts.single())
        // The canonical choice keeps the first spelling; the others stay stored.
        assertEquals(CategoryLabel.Custom("Pets"), one.label("custom-c"))
        assertEquals(3, one.stored.size)
    }

    @Test
    fun aRogueCustomNamedLikeABuiltInResolvesToTheBuiltInEverywhere() {
        val rogue = custom("custom-groc", "Groceries")
        val catalog = ExpenseCategoryCatalog(listOf(rogue))
        assertEquals(8, catalog.choices.size)
        assertEquals(ExpenseCategory.GROCERIES, catalog.find("custom-groc"))
        assertEquals("groceries", catalog.canonicalId("custom-groc"))
        assertEquals(CategoryLabel.BuiltIn(ExpenseCategory.GROCERIES), catalog.label("custom-groc"))
        assertEquals(listOf("Groceries", "groceries"), catalog.searchTerms("custom-groc"))
        assertEquals(ExpenseCategory.GROCERIES, catalog.conflicts.single().canonical)
    }
}
