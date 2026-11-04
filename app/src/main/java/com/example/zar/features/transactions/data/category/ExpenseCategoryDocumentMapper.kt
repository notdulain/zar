package com.example.zar.features.transactions.data.category

import com.example.zar.features.transactions.domain.category.CategoryNameRules
import com.example.zar.features.transactions.domain.manual.model.CategoryIcon
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
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
        )
    }.getOrNull()
}
