package com.example.zar.features.transactions.domain.category

import java.text.Normalizer
import java.util.Locale

enum class CategoryNameProblem { EMPTY, TOO_LONG, INVALID_CHARACTERS, DUPLICATE }

sealed interface CategoryNameCheck {
    /** [name] is the accepted display spelling (outer whitespace trimmed); [key] is the comparison key. */
    data class Valid(val name: String, val key: String) : CategoryNameCheck

    data class Invalid(val problem: CategoryNameProblem) : CategoryNameCheck
}

/**
 * Name rules for a custom expense category. The same limits are checked again by firestore.rules, but the rules can
 * only bound the stored string: the code-point count, control characters and Unicode normalisation are decided here.
 */
object CategoryNameRules {
    /** Counted in Unicode code points, so an emoji or a Sinhala letter with marks is not penalised twice. */
    const val MAX_LENGTH = 40

    fun validate(raw: String, catalog: ExpenseCategoryCatalog): CategoryNameCheck {
        val name = raw.trim()
        if (name.isEmpty()) return CategoryNameCheck.Invalid(CategoryNameProblem.EMPTY)
        if (name.codePointCount(0, name.length) > MAX_LENGTH) return CategoryNameCheck.Invalid(CategoryNameProblem.TOO_LONG)
        if (name.codePoints().anyMatch(::isForbidden)) return CategoryNameCheck.Invalid(CategoryNameProblem.INVALID_CHARACTERS)
        val key = keyOf(name)
        if (catalog.hasName(key)) return CategoryNameCheck.Invalid(CategoryNameProblem.DUPLICATE)
        return CategoryNameCheck.Valid(name, key)
    }

    /**
     * Case-insensitive, width- and compatibility-insensitive comparison key: NFKC, lower case (root locale, so
     * Turkish i is not special) and runs of whitespace collapsed to one space. The display name keeps its spelling.
     */
    fun keyOf(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE_RUN, " ")

    private val WHITESPACE_RUN = Regex("\\s+")

    private fun isForbidden(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.CONTROL.toInt(), Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt() -> true
        else -> false
    }
}
