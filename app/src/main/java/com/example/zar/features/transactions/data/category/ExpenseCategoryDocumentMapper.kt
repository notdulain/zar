package com.example.zar.features.transactions.data.category

import com.example.zar.features.transactions.domain.category.CategoryNameRules
import com.example.zar.features.transactions.domain.manual.model.CategoryIcon
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import java.time.Instant

/**
 * Converts a custom expense category to and from a Firestore map, using the field names in firestore.rules.
 * The reader returns null for a malformed document. It recomputes the comparison key from the name, so a document
 * written with a different normalisation still groups with equal names; the stored `nameKey` is a rules-side guard.
 */
object ExpenseCategoryDocumentMapper {
    const val SOURCE_CUSTOM = "CUSTOM"

    fun toMap(category: CustomExpenseCategory): Map<String, Any> = buildMap {
        put("id", category.id)
        put("name", category.name)
        put("nameKey", category.nameKey)
        put("source", SOURCE_CUSTOM)
        put("createdAt", category.createdAt.toEpochMilli())
        // The default icon is left out, so those categories stay valid under rules that predate the icon field.
        if (category.icon != CategoryIcon.DEFAULT) put("icon", category.icon.key)
        if (category.archived) put("archived", true)
    }

    fun fromMap(map: Map<String, Any?>): CustomExpenseCategory? = runCatching {
        val id = map["id"] as String
        val name = (map["name"] as String).trim()
        require(CustomCategoryIds.isCustom(id) && name.isNotEmpty() && map["source"] == SOURCE_CUSTOM)
        CustomExpenseCategory(
            id = id,
            name = name,
            nameKey = CategoryNameRules.keyOf(name),
            createdAt = Instant.ofEpochMilli((map["createdAt"] as Number).toLong()),
            icon = CategoryIcon.fromKey(map["icon"] as? String),
            archived = map["archived"] == true,
        )
    }.getOrNull()
}

/**
 * A built-in category the person removed from their lists. It is stored next to their own categories, marked by
 * [SOURCE] and keyed by the built-in's own ID, so old entries still resolve and the name can be restored later.
 */
object HiddenBuiltInDocumentMapper {
    const val SOURCE = "HIDDEN_BUILT_IN"

    fun toMap(id: String, createdAtMillis: Long): Map<String, Any> =
        mapOf("id" to id, "source" to SOURCE, "createdAt" to createdAtMillis)

    /** The hidden built-in's ID, or null when [map] is not a hidden-built-in document. */
    fun idOf(map: Map<String, Any?>): String? {
        if (map["source"] != SOURCE) return null
        val id = map["id"] as? String ?: return null
        return id.takeIf { known -> ExpenseCategory.entries.any { it.id == known } }
    }
}
