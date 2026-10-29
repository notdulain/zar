package com.example.zar.features.transactions.data.category

import com.example.zar.features.transactions.domain.category.CategoryNameRules
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseCategoryDocumentMapperTest {

    private val pets = CustomExpenseCategory(
        id = "custom-5f2c9a1e-7d44",
        name = "Pets 🐶",
        nameKey = CategoryNameRules.keyOf("Pets 🐶"),
        createdAt = Instant.ofEpochMilli(1_790_000_000_000),
    )

    @Test
    fun writesExactlyTheFieldsTheRulesAllow() {
        assertEquals(
            setOf("id", "name", "nameKey", "source", "createdAt"),
            ExpenseCategoryDocumentMapper.toMap(pets).keys,
        )
        assertEquals("CUSTOM", ExpenseCategoryDocumentMapper.toMap(pets)["source"])
        assertEquals(1_790_000_000_000, ExpenseCategoryDocumentMapper.toMap(pets)["createdAt"])
    }

    @Test
    fun roundTrips() {
        assertEquals(pets, ExpenseCategoryDocumentMapper.fromMap(ExpenseCategoryDocumentMapper.toMap(pets)))
    }

    @Test
    fun readsNumbersFirestoreReturnsAsLongOrInt() {
        val map = ExpenseCategoryDocumentMapper.toMap(pets) + ("createdAt" to 1_790_000_000L)
        assertEquals(Instant.ofEpochMilli(1_790_000_000L), ExpenseCategoryDocumentMapper.fromMap(map)?.createdAt)
    }

    @Test
    fun recomputesTheKeyFromTheNameSoDriftingKeysStillGroup() {
        val drifted = ExpenseCategoryDocumentMapper.toMap(pets) + ("nameKey" to "something else")
        assertEquals(pets.nameKey, ExpenseCategoryDocumentMapper.fromMap(drifted)?.nameKey)
    }

    @Test
    fun skipsMalformedDocuments() {
        val good = ExpenseCategoryDocumentMapper.toMap(pets)
        assertNull(ExpenseCategoryDocumentMapper.fromMap(good + ("id" to "groceries")))
        assertNull(ExpenseCategoryDocumentMapper.fromMap(good + ("name" to "  ")))
        assertNull(ExpenseCategoryDocumentMapper.fromMap(good + ("source" to "BUILT_IN")))
        assertNull(ExpenseCategoryDocumentMapper.fromMap(good - "createdAt"))
        assertNull(ExpenseCategoryDocumentMapper.fromMap(good + ("name" to 4)))
    }
}
