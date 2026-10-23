package com.example.zar.features.rules.domain

import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.rules.domain.model.CategoryRuleId
import com.example.zar.features.rules.domain.model.NotOfferedReason
import com.example.zar.features.rules.domain.model.OfferDecision
import com.example.zar.features.rules.domain.model.RuleMatch
import com.example.zar.features.rules.domain.model.RuleScope
import com.example.zar.features.rules.rule
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantKeyTest {
    @Test
    fun theSameNameInAnyCaseOrSpacingGivesTheSameKey() {
        assertEquals("keells super", MerchantKey.of("Keells Super"))
        assertEquals("keells super", MerchantKey.of("  KEELLS   super "))
    }

    @Test
    fun surroundingPunctuationIsIgnoredButTheNameInsideIsKept() {
        assertEquals("keells super", MerchantKey.of("** Keells Super #"))
        assertEquals("mc&s 12", MerchantKey.of("MC&S 12"))
    }

    @Test
    fun aShorterNameIsNeverTheSameAsALongerOne() {
        assertFalse(MerchantKey.of("Keells") == MerchantKey.of("Keells Super"))
    }

    @Test
    fun blankOrTooShortNamesHaveNoKey() {
        assertNull(MerchantKey.of(null))
        assertNull(MerchantKey.of("   "))
        assertNull(MerchantKey.of("ab"))
        assertNull(MerchantKey.of("--"))
        assertNotNull(MerchantKey.of("KFC"))
    }

    @Test
    fun onlyTheExactGeneralNameIsGeneral() {
        assertTrue(MerchantKey.isGeneric(MerchantKey.of("PickMe")!!))
        assertTrue(MerchantKey.isGeneric(MerchantKey.of(" UBER ")!!))
        assertFalse(MerchantKey.isGeneric(MerchantKey.of("PickMe Food")!!))
        assertFalse(MerchantKey.isGeneric(MerchantKey.of("Keells Super")!!))
    }
}

class CategoryRuleMatcherTest {
    @Test
    fun anEnabledRuleFilesTheExactName() {
        val keells = rule("Keells Super")
        assertEquals(RuleMatch.Applied(keells), CategoryRuleMatcher.match(listOf(keells), "keells  super", cash.id))
    }

    @Test
    fun aPartialNameDoesNotMatch() {
        val rules = listOf(rule("Keells Super"))
        assertEquals(RuleMatch.NoRule, CategoryRuleMatcher.match(rules, "Keells", cash.id))
        assertEquals(RuleMatch.NoRule, CategoryRuleMatcher.match(rules, "Keells Super Nugegoda", cash.id))
    }

    @Test
    fun aSwitchedOffRuleFilesNothing() {
        assertEquals(RuleMatch.NoRule, CategoryRuleMatcher.match(listOf(rule("Keells Super", enabled = false)), "Keells Super", cash.id))
    }

    @Test
    fun aRuleForOneAccountDoesNotApplyToAnother() {
        val onlyCash = rule("Keells Super", account = cash.id)
        assertEquals(RuleMatch.Applied(onlyCash), CategoryRuleMatcher.match(listOf(onlyCash), "Keells Super", cash.id))
        assertEquals(RuleMatch.NoRule, CategoryRuleMatcher.match(listOf(onlyCash), "Keells Super", bank.id))
    }

    @Test
    fun theAccountRuleBeatsTheAnyAccountRule() {
        val any = rule("Keells Super", category = "groceries", id = "any")
        val onlyCash = rule("Keells Super", category = "coffee-dining", id = "cash", account = cash.id)
        assertEquals(RuleMatch.Applied(onlyCash), CategoryRuleMatcher.match(listOf(any, onlyCash), "Keells Super", cash.id))
        assertEquals(RuleMatch.Applied(any), CategoryRuleMatcher.match(listOf(any, onlyCash), "Keells Super", bank.id))
    }

    @Test
    fun aGeneralNameIsLeftUnresolvedEvenIfARuleExists() {
        val pickMe = rule("PickMe", category = "transport")
        assertEquals(RuleMatch.TooGeneral, CategoryRuleMatcher.match(listOf(pickMe), "PickMe", cash.id))
        assertTrue(CategoryRuleMatcher.match(listOf(rule("PickMe Food", category = "coffee-dining")), "PickMe Food", cash.id) is RuleMatch.Applied)
    }

    @Test
    fun rulesOfTheSameWeightThatDisagreeAreAConflictAndFileNothing() {
        val a = rule("Keells Super", category = "groceries", id = "a")
        val b = rule("Keells Super", category = "coffee-dining", id = "b")
        val match = CategoryRuleMatcher.match(listOf(a, b), "Keells Super", cash.id)
        assertEquals(RuleMatch.Conflict(listOf(a, b)), match)
    }

    @Test
    fun rulesOfTheSameWeightThatAgreeUseTheNewestVersion() {
        val old = rule("Keells Super", id = "a", version = 1)
        val newer = rule("Keells Super", id = "b", version = 3)
        assertEquals(RuleMatch.Applied(newer), CategoryRuleMatcher.match(listOf(old, newer), "Keells Super", cash.id))
    }

    @Test
    fun aBlankNameHasNoRule() {
        assertEquals(RuleMatch.NoRule, CategoryRuleMatcher.match(listOf(rule()), null, cash.id))
        assertEquals(RuleMatch.NoRule, CategoryRuleMatcher.match(listOf(rule()), "  ", cash.id))
    }
}

class RuleOfferRulesTest {
    private val now = Instant.parse("2026-10-23T04:00:00Z")

    @Test
    fun aPreciseNameWithACategoryIsOffered() {
        val decision = RuleOfferRules.decide("Keells Super", "groceries", cash.id, emptyList())
        val offer = (decision as OfferDecision.Offer).offer
        assertEquals("Keells Super", offer.displayName)
        assertEquals("keells super", offer.merchantKey)
        assertEquals("groceries", offer.categoryId)
        assertEquals(cash.id, offer.accountId)
        assertNull(offer.existing)
    }

    @Test
    fun nothingIsOfferedForABlankOrGeneralNameOrNoCategory() {
        assertEquals(OfferDecision.NotOffered(NotOfferedReason.NO_NAME), RuleOfferRules.decide("", "groceries", cash.id, emptyList()))
        assertEquals(OfferDecision.NotOffered(NotOfferedReason.TOO_GENERAL), RuleOfferRules.decide("PickMe", "transport", cash.id, emptyList()))
        assertEquals(OfferDecision.NotOffered(NotOfferedReason.NO_CATEGORY), RuleOfferRules.decide("Keells Super", null, cash.id, emptyList()))
    }

    @Test
    fun nothingIsOfferedWhenARuleAlreadyCoversTheChoice() {
        val rules = listOf(rule("Keells Super"))
        assertEquals(OfferDecision.NotOffered(NotOfferedReason.ALREADY_REMEMBERED), RuleOfferRules.decide("keells super", "groceries", cash.id, rules))
    }

    @Test
    fun aDifferentCategoryOffersToChangeTheExistingRuleInsteadOfAddingASecond() {
        val existing = rule("Keells Super", category = "groceries")
        val offer = (RuleOfferRules.decide("Keells Super", "coffee-dining", cash.id, listOf(existing)) as OfferDecision.Offer).offer
        assertEquals(existing, offer.existing)
        val changed = RuleOfferRules.remember(offer, RuleScope.ANY_ACCOUNT, CategoryRuleId("new"), now)
        assertEquals(existing.id, changed.id)
        assertEquals("coffee-dining", changed.categoryId)
        assertEquals(2, changed.version)
        assertEquals(existing.createdAt, changed.createdAt)
    }

    @Test
    fun aSwitchedOffRuleIsBroughtBackWhenTheSameChoiceIsRememberedAgain() {
        val off = rule("Keells Super", enabled = false)
        val offer = (RuleOfferRules.decide("Keells Super", "groceries", cash.id, listOf(off)) as OfferDecision.Offer).offer
        val back = RuleOfferRules.remember(offer, RuleScope.ANY_ACCOUNT, CategoryRuleId("new"), now)
        assertTrue(back.enabled)
        assertEquals(2, back.version)
    }

    @Test
    fun rememberingForThisAccountAddsARuleLimitedToIt() {
        val offer = (RuleOfferRules.decide("Keells Super", "groceries", cash.id, emptyList()) as OfferDecision.Offer).offer
        val rule = RuleOfferRules.remember(offer, RuleScope.THIS_ACCOUNT, CategoryRuleId("r1"), now)
        assertEquals(cash.id, rule.accountId)
        assertEquals(1, rule.version)
        assertTrue(rule.enabled)
    }

    @Test
    fun editingRaisesTheVersionOnlyWhenSomethingChanged() {
        val rule = rule("Keells Super", version = 2)
        assertEquals(rule, RuleOfferRules.edit(rule, "groceries", null, true, now))
        val edited = RuleOfferRules.edit(rule, "groceries", null, false, now)
        assertEquals(3, edited.version)
        assertFalse(edited.enabled)
        assertEquals(now, edited.updatedAt)
    }
}

class RuleCardRulesTest {
    private fun expense(merchant: String?, category: String?, ruleId: String? = null, version: Int? = null, account: com.example.zar.core.domain.model.Account = cash) =
        TestLedger.tx("e1", TransactionType.EXPENSE, account = account)
            .copy(merchant = merchant, categoryId = category, appliedRuleId = ruleId, appliedRuleVersion = version)

    private fun entry(tx: com.example.zar.core.domain.model.Transaction, vararg corrections: com.example.zar.core.domain.model.Correction, voided: Boolean = false) =
        LedgerProjector.project(listOf(tx), corrections.toList(), if (voided) listOf(TestLedger.void(tx.id.value)) else emptyList(), emptyList()).single()

    @Test
    fun anExpenseTheNameOfWhichIsPreciseGetsAnOffer() {
        val card = RuleCardRules.of(entry(expense("Keells Super", "groceries")), emptyList())
        assertTrue(card is RuleCard.Offer)
    }

    @Test
    fun aGeneralNameGetsTheTooGeneralCard() {
        assertEquals(RuleCard.TooGeneral("PickMe"), RuleCardRules.of(entry(expense("PickMe", "transport")), emptyList()))
    }

    @Test
    fun anExpenseFiledByARuleShowsThatRuleAndTheVersionThatFiledIt() {
        val rules = listOf(rule("Keells Super", version = 4))
        val card = RuleCardRules.of(entry(expense("Keells Super", "groceries", "r-keells-super", 2)), rules)
        assertEquals(RuleCard.Applied("Keells Super", "groceries", 2), card)
    }

    @Test
    fun aCorrectionThatChangesTheCategoryEndsTheAppliedCard() {
        val tx = expense("Keells Super", "groceries", "r-keells-super", 2)
        val fix = TestLedger.correction("c1", "e1", TransactionChanges(categoryId = FieldUpdate.Set("coffee-dining")))
        val card = RuleCardRules.of(entry(tx, fix), listOf(rule("Keells Super", version = 2)))
        assertTrue(card is RuleCard.Offer)
    }

    @Test
    fun voidedEntriesAndOtherTypesHaveNoCard() {
        assertEquals(RuleCard.Hidden, RuleCardRules.of(entry(expense("Keells Super", "groceries"), voided = true), emptyList()))
        val income = TestLedger.tx("i1", TransactionType.INCOME)
        assertEquals(RuleCard.Hidden, RuleCardRules.of(entry(income), emptyList()))
    }

    @Test
    fun aChoiceARuleAlreadyCoversHasNoCardUnlessItFiledThisEntry() {
        val rules = listOf(rule("Keells Super"))
        assertEquals(RuleCard.Hidden, RuleCardRules.of(entry(expense("Keells Super", "groceries")), rules))
    }
}
