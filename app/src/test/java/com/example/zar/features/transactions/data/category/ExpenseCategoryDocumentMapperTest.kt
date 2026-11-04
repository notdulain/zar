package com.example.zar.features.transactions.data.category

import com.example.zar.features.transactions.domain.category.CategoryNameRules
import com.example.zar.features.transactions.domain.manual.model.CategoryIcon
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

    @Test
    fun defaultIconIsLeftOutSoOlderRulesStillAcceptTheWrite() {
        assertEquals(false, "icon" in ExpenseCategoryDocumentMapper.toMap(pets))
    }

    @Test
    fun aChosenIconIsWrittenByKeyAndRoundTrips() {
        val withIcon = pets.copy(icon = CategoryIcon.PIGGY_BANK)
        assertEquals("piggy_bank", ExpenseCategoryDocumentMapper.toMap(withIcon)["icon"])
        assertEquals(withIcon, ExpenseCategoryDocumentMapper.fromMap(ExpenseCategoryDocumentMapper.toMap(withIcon)))
    }

    @Test
    fun missingOrUnknownIconReadsAsTheDefault() {
        val good = ExpenseCategoryDocumentMapper.toMap(pets)
        assertEquals(CategoryIcon.DEFAULT, ExpenseCategoryDocumentMapper.fromMap(good)?.icon)
        assertEquals(CategoryIcon.DEFAULT, ExpenseCategoryDocumentMapper.fromMap(good + ("icon" to "from-the-future"))?.icon)
    }

    @Test
    fun iconKeysAreUniqueAndStable() {
        assertEquals(CategoryIcon.entries.size, CategoryIcon.entries.map { it.key }.toSet().size)
        CategoryIcon.entries.forEach { assertEquals(it, CategoryIcon.fromKey(it.key)) }
    }

    @Test
    fun anArchivedCategoryWritesAndReadsBackAsArchived() {
        val gone = pets.copy(archived = true)
        assertEquals(true, ExpenseCategoryDocumentMapper.toMap(gone)["archived"])
        assertEquals(gone, ExpenseCategoryDocumentMapper.fromMap(ExpenseCategoryDocumentMapper.toMap(gone)))
        assertEquals(false, "archived" in ExpenseCategoryDocumentMapper.toMap(pets))
        assertEquals(false, ExpenseCategoryDocumentMapper.fromMap(ExpenseCategoryDocumentMapper.toMap(pets))?.archived)
    }
}
