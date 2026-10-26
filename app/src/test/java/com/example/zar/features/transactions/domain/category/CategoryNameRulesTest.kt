package com.example.zar.features.transactions.domain.category

import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryNameRulesTest {

    private val catalog = ExpenseCategoryCatalog(listOf(custom("custom-pets", "Pets")))

    private fun custom(id: String, name: String, at: Long = 1_000) =
        CustomExpenseCategory(id, name, CategoryNameRules.keyOf(name), Instant.ofEpochMilli(at))

    private fun problem(raw: String, c: ExpenseCategoryCatalog = catalog) =
        (CategoryNameRules.validate(raw, c) as CategoryNameCheck.Invalid).problem

    @Test
    fun acceptsATrimmedNameAndKeepsItsSpelling() {
        val result = CategoryNameRules.validate("  Gym Fees \n", catalog) as CategoryNameCheck.Valid
        assertEquals("Gym Fees", result.name)
        assertEquals("gym fees", result.key)
    }

    @Test
    fun rejectsEmptyAndWhitespaceOnlyNames() {
        assertEquals(CategoryNameProblem.EMPTY, problem(""))
        assertEquals(CategoryNameProblem.EMPTY, problem("  \t\n "))
    }

    @Test
    fun limitIsFortyCodePointsNotUtf16Units() {
        assertTrue(CategoryNameRules.validate("a".repeat(40), catalog) is CategoryNameCheck.Valid)
        assertEquals(CategoryNameProblem.TOO_LONG, problem("a".repeat(41)))
        // 40 emoji are 80 UTF-16 units but 40 code points: accepted. 41 are not.
        assertTrue(CategoryNameRules.validate("🐶".repeat(40), catalog) is CategoryNameCheck.Valid)
        assertEquals(CategoryNameProblem.TOO_LONG, problem("🐶".repeat(41)))
        // Sinhala text counts by code point.
        assertTrue(CategoryNameRules.validate("කෑම".repeat(13), catalog) is CategoryNameCheck.Valid)
    }

    @Test
    fun rejectsLineBreaksAndControlCharacters() {
        assertEquals(CategoryNameProblem.INVALID_CHARACTERS, problem("Pet\nfood"))
        assertEquals(CategoryNameProblem.INVALID_CHARACTERS, problem("Pet\tfood"))
        assertEquals(CategoryNameProblem.INVALID_CHARACTERS, problem("Pet\u0000food"))
        assertEquals(CategoryNameProblem.INVALID_CHARACTERS, problem("Pet food"))
    }

    @Test
    fun rejectsDuplicatesIgnoringCaseSpacingAndWidth() {
        assertEquals(CategoryNameProblem.DUPLICATE, problem(" pets "))
        assertEquals(CategoryNameProblem.DUPLICATE, problem("PETS"))
        assertEquals(CategoryNameProblem.DUPLICATE, problem("Ｐｅｔｓ"))
        val spaced = ExpenseCategoryCatalog(listOf(custom("custom-gym", "Gym  Fees")))
        assertEquals(CategoryNameProblem.DUPLICATE, problem("gym fees", spaced))
    }

    @Test
    fun rejectsBuiltInNamesButNotOtherWords() {
        assertEquals(CategoryNameProblem.DUPLICATE, problem("groceries"))
        assertEquals(CategoryNameProblem.DUPLICATE, problem("Coffee & Dining"))
        assertEquals(CategoryNameProblem.DUPLICATE, problem("RENT", ExpenseCategoryCatalog.BUILT_IN_ONLY))
        assertTrue(CategoryNameRules.validate("Rent deposit", catalog) is CategoryNameCheck.Valid)
    }

    @Test
    fun lowerCaseUsesTheRootLocale() {
        // Turkish dotted/dotless i must not make "BILLS" and "bills" differ.
        assertEquals(CategoryNameRules.keyOf("BILLS"), CategoryNameRules.keyOf("bills"))
    }
}
