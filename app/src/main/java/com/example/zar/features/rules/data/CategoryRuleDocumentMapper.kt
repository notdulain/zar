package com.example.zar.features.rules.data

import com.example.zar.core.domain.model.AccountId
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.model.CategoryRuleId
import java.time.Instant

/**
 * Converts a category rule to and from a Firestore map, using the field names in firestore.rules.
 * The reader returns null for a malformed document.
 */
object CategoryRuleDocumentMapper {

    fun toMap(rule: CategoryRule): Map<String, Any> = buildMap {
        put("id", rule.id.value)
        put("merchantKey", rule.merchantKey)
        put("displayName", rule.displayName)
        put("categoryId", rule.categoryId)
        rule.accountId?.let { put("accountId", it.value) }
        put("enabled", rule.enabled)
        put("version", rule.version)
        put("createdAt", rule.createdAt.toEpochMilli())
        put("updatedAt", rule.updatedAt.toEpochMilli())
    }

    fun fromMap(map: Map<String, Any?>): CategoryRule? = runCatching {
        CategoryRule(
            id = CategoryRuleId(map["id"] as String),
            merchantKey = map["merchantKey"] as String,
            displayName = map["displayName"] as String,
            categoryId = map["categoryId"] as String,
            accountId = (map["accountId"] as String?)?.let(::AccountId),
            enabled = map["enabled"] as Boolean,
            version = (map["version"] as Number).toInt(),
            createdAt = Instant.ofEpochMilli((map["createdAt"] as Number).toLong()),
            updatedAt = Instant.ofEpochMilli((map["updatedAt"] as Number).toLong()),
        )
    }.getOrNull()
}
