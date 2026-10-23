package com.example.zar.features.rules.data

import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.features.rules.rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryRuleDocumentMapperTest {
    @Test
    fun roundTripsARuleForEveryAccount() {
        val original = rule("Keells Super", version = 3)
        val map = CategoryRuleDocumentMapper.toMap(original)
        assertFalse("A rule for every account has no account field", map.containsKey("accountId"))
        assertEquals(original, CategoryRuleDocumentMapper.fromMap(map))
    }

    @Test
    fun roundTripsARuleLimitedToOneAccountAndSwitchedOff() {
        val original = rule("Dialog", category = "bills", account = cash.id, enabled = false)
        val map = CategoryRuleDocumentMapper.toMap(original)
        assertEquals("cash", map["accountId"])
        assertEquals(false, map["enabled"])
        assertEquals(original, CategoryRuleDocumentMapper.fromMap(map))
    }

    @Test
    fun usesTheFieldNamesFirestoreRulesExpect() {
        val map = CategoryRuleDocumentMapper.toMap(rule("Keells Super"))
        assertEquals(
            setOf("id", "merchantKey", "displayName", "categoryId", "enabled", "version", "createdAt", "updatedAt"),
            map.keys,
        )
        assertEquals("keells super", map["merchantKey"])
    }

    @Test
    fun aMalformedDocumentIsSkipped() {
        assertNull(CategoryRuleDocumentMapper.fromMap(mapOf("id" to "x")))
        assertNull(CategoryRuleDocumentMapper.fromMap(CategoryRuleDocumentMapper.toMap(rule("Keells Super")) + ("version" to 0)))
        assertNull(CategoryRuleDocumentMapper.fromMap(CategoryRuleDocumentMapper.toMap(rule("Keells Super")) + ("merchantKey" to " ")))
    }
}
