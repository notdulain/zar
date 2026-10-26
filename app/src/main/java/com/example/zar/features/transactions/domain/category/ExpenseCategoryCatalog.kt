package com.example.zar.features.transactions.domain.category

import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategoryChoice

/** How a saved category ID reads on screen. */
sealed interface CategoryLabel {
    /** A built-in; the UI shows its string resource. */
    data class BuiltIn(val category: ExpenseCategory) : CategoryLabel

    data class Custom(val name: String) : CategoryLabel

    /**
     * The catalog has no record for this ID: a legacy or imported ID, or a custom category that has not arrived
     * yet. The entry stays visible and keeps its ID; the UI shows a safe generic label.
     */
    data class Unavailable(val id: String) : CategoryLabel
}

/** Several stored custom categories whose names collide. Nothing is deleted; every ID keeps resolving. */
data class CategoryConflict(val canonical: ExpenseCategoryChoice, val duplicateIds: List<String>)

/**
 * The built-in categories plus the user's [stored] custom ones.
 *
 * Order is built-ins in their fixed order, then customs by creation time and ID, so the picker is stable.
 *
 * Two devices can create the same name offline, so [stored] may hold several documents with one normalised name.
 * Reconciliation is deterministic and never rewrites or deletes anything: a built-in wins over a custom with its
 * name, and among customs the earliest by (createdAt, id) is canonical. The others stay stored and keep resolving
 * (to the canonical category's name), so an expense that references one still shows a label; they are left out of
 * [choices] and reported in [conflicts] so the app can offer a recoverable merge later.
 */
data class ExpenseCategoryCatalog(
    val stored: List<CustomExpenseCategory> = emptyList(),
    /**
     * True when the live read failed and [stored] is the last list read successfully. The repository keeps retrying,
     * so the names stay usable; a screen can offer a recovery notice instead of dropping the user's own categories.
     */
    val degraded: Boolean = false,
) {

    private val builtInKeys: Map<String, ExpenseCategory> =
        ExpenseCategory.entries.associateBy { CategoryNameRules.keyOf(it.defaultName) }

    private val ordered: List<CustomExpenseCategory> =
        stored.sortedWith(compareBy<CustomExpenseCategory> { it.createdAt }.thenBy { it.id })

    private val canonicalByKey: Map<String, ExpenseCategoryChoice> = buildMap {
        builtInKeys.forEach { (key, category) -> put(key, category) }
        ordered.forEach { putIfAbsent(it.nameKey, it) }
    }

    private fun canonicalOf(category: CustomExpenseCategory): ExpenseCategoryChoice =
        canonicalByKey.getValue(category.nameKey)

    /** The categories to offer: built-ins first, then non-duplicate customs. */
    val choices: List<ExpenseCategoryChoice> = ExpenseCategory.entries + ordered.filter { canonicalOf(it) == it }

    val customs: List<CustomExpenseCategory> = ordered.filter { canonicalOf(it) == it }

    val conflicts: List<CategoryConflict> = ordered
        .filter { canonicalOf(it) != it }
        .groupBy { canonicalOf(it) }
        .map { (canonical, duplicates) -> CategoryConflict(canonical, duplicates.map { it.id }) }

    private val byId: Map<String, ExpenseCategoryChoice> = buildMap {
        ExpenseCategory.entries.forEach { put(it.id, it) }
        ordered.forEach { putIfAbsent(it.id, canonicalOf(it)) }
    }

    /** The choice for [id], or null for an unknown one. A duplicate's ID resolves to its canonical category. */
    fun find(id: String?): ExpenseCategoryChoice? = id?.let(byId::get)

    /** The ID the picker uses for [id]: itself unless it is a reconciled duplicate. Unknown IDs are unchanged. */
    fun canonicalId(id: String): String = byId[id]?.id ?: id

    /** True when [key] (see [CategoryNameRules.keyOf]) is a built-in or stored custom name. */
    fun hasName(key: String): Boolean = key in canonicalByKey

    fun label(id: String?): CategoryLabel? {
        if (id == null) return null
        return when (val choice = find(id)) {
            is ExpenseCategory -> CategoryLabel.BuiltIn(choice)
            is CustomExpenseCategory -> CategoryLabel.Custom(choice.name)
            else -> CategoryLabel.Unavailable(id)
        }
    }

    /**
     * What Activity search matches for an entry's category: the built-in's name (and its ID words, as before) or the
     * custom name, never an opaque `custom-…` ID. Legacy IDs still match by their words ("transfer-fee" as
     * "transfer fee"). Empty when nothing readable is known, so an unresolved custom ID never matches a search.
     */
    fun searchTerms(id: String?): List<String> = when (val label = label(id)) {
        null -> emptyList()
        is CategoryLabel.BuiltIn -> listOf(label.category.defaultName, label.category.id.replace('-', ' '))
        is CategoryLabel.Custom -> listOf(label.name)
        is CategoryLabel.Unavailable -> listOfNotNull(label.id.takeUnless { CustomCategoryIds.isCustom(it) }?.replace('-', ' '))
    }

    companion object {
        /** What is shown before the user's catalog has arrived, or when nobody is signed in. */
        val BUILT_IN_ONLY = ExpenseCategoryCatalog()
    }
}
