package com.example.zar.features.transactions.domain.category

import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCategoryCatalogTest {

    private fun custom(id: String, name: String, at: Long = 1_000) =
        CustomExpenseCategory(id, name, CategoryNameRules.keyOf(name), Instant.ofEpochMilli(at))

    @Test
    fun builtInsKeepTheirIdsAndOrderThenCustomsByCreationThenId() {
        val catalog = ExpenseCategoryCatalog(
            listOf(custom("custom-b", "Beta", 2_000), custom("custom-z", "Zed", 1_000), custom("custom-a", "Alpha", 2_000)),
        )
        assertEquals(
            listOf("coffee-dining", "groceries", "transport", "bills", "rent", "season-ticket", "subscriptions", "other"),
            catalog.choices.take(8).map { it.id },
        )
        assertEquals(listOf("custom-z", "custom-a", "custom-b"), catalog.choices.drop(8).map { it.id })
    }

    @Test
    fun catalogOrderDoesNotDependOnStoredOrder() {
        val a = custom("custom-a", "Alpha", 1_000)
        val b = custom("custom-b", "Beta", 1_000)
        assertEquals(ExpenseCategoryCatalog(listOf(a, b)).choices, ExpenseCategoryCatalog(listOf(b, a)).choices)
    }

    @Test
    fun customCategoryHasNoUsualClassEvenWhenNamedLikeABuiltIn() {
        val rentLike = custom("custom-rent", "Rent deposit")
        assertNull(rentLike.usualClass)
        assertEquals(SpendingClass.COMMITTED, ExpenseCategory.RENT.usualClass)
    }

    @Test
    fun resolvesLabelsAndKeepsUnknownIdsSafe() {
        val catalog = ExpenseCategoryCatalog(listOf(custom("custom-pets", "Pets")))
        assertEquals(CategoryLabel.BuiltIn(ExpenseCategory.GROCERIES), catalog.label("groceries"))
        assertEquals(CategoryLabel.Custom("Pets"), catalog.label("custom-pets"))
        assertEquals(CategoryLabel.Unavailable("custom-missing"), catalog.label("custom-missing"))
        assertEquals(CategoryLabel.Unavailable("legacy-fuel"), catalog.label("legacy-fuel"))
        assertNull(catalog.label(null))
    }

    @Test
    fun searchUsesTheResolvedNameNeverAnOpaqueCustomId() {
        val catalog = ExpenseCategoryCatalog(listOf(custom("custom-1234abcd", "Pets")))
        assertEquals("Pets", catalog.searchText("custom-1234abcd"))
        assertEquals("Groceries", catalog.searchText("groceries"))
        assertNull(catalog.searchText("custom-ffff0000"))
        // Legacy words stay searchable like before.
        assertEquals("transfer fee", catalog.searchText("transfer-fee"))
    }

    @Test
    fun beforeTheCatalogArrivesCustomIdsAreUnavailableNotHidden() {
        assertEquals(CategoryLabel.Unavailable("custom-pets"), ExpenseCategoryCatalog.BUILT_IN_ONLY.label("custom-pets"))
        assertEquals(8, ExpenseCategoryCatalog.BUILT_IN_ONLY.choices.size)
    }

    @Test
    fun concurrentDuplicateNamesResolveToTheEarliestAndKeepEveryReference() {
        val first = custom("custom-b", "Pets", at = 1_000)
        val late = custom("custom-a", "pets ", at = 5_000)
        val tie = custom("custom-c", "PETS", at = 1_000)
        val catalog = ExpenseCategoryCatalog(listOf(late, tie, first))

        // Earliest (createdAt, id) wins: b before c at the same time.
        assertEquals(listOf(first), catalog.customs)
        assertEquals(listOf(first), catalog.choices.drop(8))
        assertEquals(listOf(CategoryConflict(first, listOf("custom-c", "custom-a"))), catalog.conflicts)
        // The duplicates still resolve, to the canonical name, and keep their own stored id on old expenses.
        assertEquals(CategoryLabel.Custom("Pets"), catalog.label("custom-a"))
        assertEquals("custom-b", catalog.canonicalId("custom-a"))
        assertEquals("custom-b", catalog.canonicalId("custom-b"))
        assertEquals("unknown", catalog.canonicalId("unknown"))
        // Nothing is dropped from what was stored.
        assertEquals(3, catalog.stored.size)
    }

    @Test
    fun aBuiltInWinsOverACustomWithTheSameName() {
        val rogue = custom("custom-groc", "groceries")
        val catalog = ExpenseCategoryCatalog(listOf(rogue))
        assertEquals(8, catalog.choices.size)
        assertEquals(CategoryLabel.BuiltIn(ExpenseCategory.GROCERIES), catalog.label("custom-groc"))
        assertEquals("groceries", catalog.canonicalId("custom-groc"))
        assertEquals(ExpenseCategory.GROCERIES, catalog.conflicts.single().canonical)
    }

    @Test
    fun generatedIdsAreStableCustomIds() {
        val id = CustomCategoryIds.newId()
        assertTrue(CustomCategoryIds.isCustom(id))
        assertTrue(id.length <= 64)
        assertTrue(ExpenseCategory.entries.none { CustomCategoryIds.isCustom(it.id) })
    }
}
