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
 * The icon a person picks for their own category. [key] is the stored name: never rename or reuse one, because
 * documents and the Firestore rules hold it. An unknown or missing key reads as [RECEIPT], the original generic icon.
 */
enum class CategoryIcon(val key: String) {
    RECEIPT("receipt"),
    FORK_KNIFE("fork_knife"),
    COFFEE("coffee"),
    BASKET("basket"),
    GROCERIES("groceries"),
    CAR("car"),
    BANK("bank"),
    HOUSE("house"),
    BARBELL("barbell"),
    TELEVISION("television"),
    PHONE("phone"),
    WIFI("wifi"),
    LIGHTNING("lightning"),
    PIGGY_BANK("piggy_bank"),
    BRIEFCASE("briefcase"),
    WALLET("wallet");

    companion object {
        val DEFAULT = RECEIPT

        fun fromKey(key: String?): CategoryIcon = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
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
    val icon: CategoryIcon = CategoryIcon.DEFAULT,
    /**
     * Deleted by the person: hidden from every picker but still stored, so an expense that used it keeps its name.
     * Never reversed on the same document; adding the name again makes a new category.
     */
    val archived: Boolean = false,
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
