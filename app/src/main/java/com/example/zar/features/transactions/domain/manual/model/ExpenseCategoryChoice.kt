package com.example.zar.features.transactions.domain.manual.model

import com.example.zar.core.domain.model.SpendingClass
import java.time.Instant
import java.util.UUID

/**
 * One expense category a person can pick: a built-in [ExpenseCategory] or one of their own
 * [CustomExpenseCategory]. [id] is what an expense stores in `Transaction.categoryId`; it never changes.
 */
interface ExpenseCategoryChoice {
    val id: String

    /** Only a hint that preselects "Counts as". Null means no suggestion; an explicit choice always wins. */
    val usualClass: SpendingClass?
}

/**
 * A category the person added. It carries no usual class, so it never infers a spending class from its name
 * (a custom "Rent" is not committed unless the person says so). [nameKey] is the normalised comparison key.
 */
data class CustomExpenseCategory(
    override val id: String,
    val name: String,
    val nameKey: String,
    val createdAt: Instant,
) : ExpenseCategoryChoice {
    override val usualClass: SpendingClass? get() = null
}

/** IDs of custom categories: `custom-<uuid>`. Built-in and income IDs never start with the prefix. */
object CustomCategoryIds {
    const val PREFIX = "custom-"

    /** Create once when the person starts adding a category and reuse it on every retry. */
    fun newId(): String = PREFIX + UUID.randomUUID()

    fun isCustom(id: String?): Boolean = id != null && id.startsWith(PREFIX)
}
