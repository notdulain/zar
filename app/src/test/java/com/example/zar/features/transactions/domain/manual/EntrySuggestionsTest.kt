package com.example.zar.features.transactions.domain.manual

import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntrySuggestionsTest {

    private fun expense(
        id: String,
        title: String?,
        day: Int = 10,
        category: String? = "transport",
        amount: Long = 850,
        account: com.example.zar.core.domain.model.Account = cash,
        spendingClass: SpendingClass = SpendingClass.DISCRETIONARY,
    ): Transaction = tx(id, TransactionType.EXPENSE, lkr(amount), account = account, date = OCT_10.withDayOfMonth(day), spendingClass = spendingClass)
        .copy(merchant = title, categoryId = category)

    @Test
    fun usesTheLatestEntryForEachTitleIgnoringCase() {
        val all = EntrySuggestions.recent(
            listOf(
                entry(expense("old", "Bus to Kandy", day = 2, amount = 800)),
                entry(expense("new", "bus to kandy", day = 9, amount = 850)),
                entry(expense("other", "Keells", day = 5, category = "groceries", account = bank)),
            ),
        )

        assertEquals(listOf("bus to kandy", "Keells"), all.map { it.title })
        assertEquals(lkr(850), all.first().amount)
        assertEquals(ExpenseCategory.GROCERIES, all.last().category)
        assertEquals(bank.id, all.last().accountId)
    }

    @Test
    fun ignoresUndoneNonExpenseAndUntitledEntries() {
        val all = EntrySuggestions.recent(
            listOf(
                entry(expense("undone", "Coffee"), voided = true),
                entry(expense("untitled", null)),
                entry(expense("blank", "   ")),
                entry(tx("salary", TransactionType.INCOME).copy(merchant = "Client")),
                entry(expense("ok", "Lunch")),
            ),
        )

        assertEquals(listOf("Lunch"), all.map { it.title })
    }

    @Test
    fun unknownCategoryIdsAreKeptAsNoCategory() {
        val all = EntrySuggestions.recent(listOf(entry(expense("e", "Fee", category = "transfer-fee"))))

        assertEquals(null, all.single().category)
    }

    @Test
    fun matchesTheStartOfATitleOnly() {
        val all = EntrySuggestions.recent(
            listOf(
                entry(expense("a", "Bus to Kandy", day = 9)),
                entry(expense("b", "Burger", day = 8)),
                entry(expense("c", "Bundle", day = 7)),
                entry(expense("d", "Cab to Bus stand", day = 6)),
            ),
        )

        assertEquals(listOf("Bus to Kandy"), EntrySuggestions.matching(all, "bus").map { it.title })
        assertEquals(listOf("Bus to Kandy", "Burger", "Bundle"), EntrySuggestions.matching(all, "bu").map { it.title })
    }

    @Test
    fun blankInputSuggestsNothingAndResultsAreCapped() {
        val many = (1..9).map { entry(expense("e$it", "Bus $it", day = it)) }
        val all = EntrySuggestions.recent(many)

        assertTrue(EntrySuggestions.matching(all, "  ").isEmpty())
        assertEquals(EntrySuggestions.MAX_SHOWN, EntrySuggestions.matching(all, "Bus").size)
    }

    @Test
    fun suggestsACustomCategoryOnlyWhenTheUsersCatalogKnowsIt() {
        val pets = com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory(
            "custom-9f8e7d6c", "Pets", com.example.zar.features.transactions.domain.category.CategoryNameRules.keyOf("Pets"), T0,
        )
        val entries = listOf(
            entry(expense("vet", "Vet", category = "custom-9f8e7d6c")),
            entry(expense("legacy", "Old thing", category = "fuel-2019")),
        )
        val withCatalog = EntrySuggestions.recent(entries, com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog(listOf(pets)))
        assertEquals(pets, withCatalog.first { it.title == "Vet" }.category)
        // A legacy or not-yet-synced ID is never replaced by Other: no category is suggested.
        assertEquals(null, withCatalog.first { it.title == "Old thing" }.category)
        assertEquals(null, EntrySuggestions.recent(entries).first { it.title == "Vet" }.category)
    }
}
