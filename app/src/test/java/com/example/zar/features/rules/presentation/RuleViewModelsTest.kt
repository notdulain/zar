package com.example.zar.features.rules.presentation

import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.features.accounts.domain.usecase.ObserveActiveAccountsUseCase
import com.example.zar.features.rules.FakeCategoryRules
import com.example.zar.features.rules.domain.RuleOfferRules
import com.example.zar.features.rules.domain.model.OfferDecision
import com.example.zar.features.rules.domain.model.RuleScope
import com.example.zar.features.rules.domain.usecase.EditCategoryRuleUseCase
import com.example.zar.features.rules.domain.usecase.RememberCategoryUseCase
import com.example.zar.features.rules.newInbox
import com.example.zar.features.rules.presentation.viewmodel.CategoryRulesViewModel
import com.example.zar.features.rules.presentation.viewmodel.RuleOfferViewModel
import com.example.zar.features.rules.rule
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.presentation.activity.FakeAccounts
import com.example.zar.features.transactions.presentation.activity.FakeConnectivity
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleOfferViewModelTest {
    private val rules = FakeCategoryRules()
    private val inbox = newInbox()
    private val accounts = FakeAccounts(listOf(bank, cash))
    private val connectivity = FakeConnectivity()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RuleOfferViewModel(
        inbox = inbox,
        remember = RememberCategoryUseCase(rules),
        observeAccounts = ObserveActiveAccountsUseCase(accounts),
        observeOnline = ObserveOnlineStatusUseCase(connectivity),
    )

    private fun offer() =
        (RuleOfferRules.decide("Keells Super", "groceries", cash.id, rules.rules.value) as OfferDecision.Offer).offer

    private fun runVm(block: suspend (RuleOfferViewModel) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        block(vm)
    }

    @Test
    fun withNothingPostedThereIsNothingToShow() = runVm { vm ->
        assertNull(vm.uiState.value.offer)
        assertFalse(vm.uiState.value.sheetOpen)
    }

    @Test
    fun aPostedOfferShowsTheCardWithTheAccountNameAndNothingIsSaved() = runVm { vm ->
        inbox.post(offer())

        val state = vm.uiState.value
        assertEquals("keells super", state.offer?.merchantKey)
        assertFalse(state.sheetOpen)
        assertEquals("Cash", state.accountName)
        assertTrue(rules.rules.value.isEmpty())
    }

    @Test
    fun tappingTheCardOpensTheSheet() = runVm { vm ->
        inbox.post(offer())
        vm.openSheet()
        assertTrue(vm.uiState.value.sheetOpen)
    }

    @Test
    fun rememberingForAnyAccountSavesOneRuleAndClosesTheOffer() = runVm { vm ->
        inbox.open(offer())
        vm.remember(RuleScope.ANY_ACCOUNT)

        val saved = rules.rules.value.single()
        assertNull(saved.accountId)
        assertEquals("groceries", saved.categoryId)
        assertNull(vm.uiState.value.offer)
    }

    @Test
    fun rememberingForThisAccountLimitsTheRuleToIt() = runVm { vm ->
        inbox.open(offer())
        vm.remember(RuleScope.THIS_ACCOUNT)

        assertEquals(cash.id, rules.rules.value.single().accountId)
    }

    @Test
    fun notNowKeepsTheRulesAsTheyWereAndClosesTheOffer() = runVm { vm ->
        inbox.open(offer())
        vm.dismiss()

        assertNull(vm.uiState.value.offer)
        assertTrue(rules.rules.value.isEmpty())
    }

    @Test
    fun aFailedSaveKeepsTheSheetAndAFreshTapSavesOnlyOneRule() = runVm { vm ->
        inbox.open(offer())
        rules.saveError = IOException("denied")
        vm.remember(RuleScope.ANY_ACCOUNT)
        assertTrue(vm.uiState.value.failed)
        assertNotNull(vm.uiState.value.offer)
        assertTrue(vm.uiState.value.sheetOpen)

        rules.saveError = null
        vm.remember(RuleScope.ANY_ACCOUNT)
        vm.remember(RuleScope.ANY_ACCOUNT)

        assertEquals(1, rules.rules.value.size)
        assertNull(vm.uiState.value.offer)
    }

    @Test
    fun theSheetKnowsWhenThePhoneIsOffline() = runVm { vm ->
        inbox.open(offer())
        connectivity.online.value = false
        assertFalse(vm.uiState.value.isOnline)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryRulesViewModelTest {
    private val rules = FakeCategoryRules()
    private val accounts = FakeAccounts(listOf(bank, cash))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun runVm(block: suspend (CategoryRulesViewModel) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val vm = CategoryRulesViewModel(rules.observeUseCase(), ObserveActiveAccountsUseCase(accounts), EditCategoryRuleUseCase(rules))
        backgroundScope.launch { vm.uiState.collect { } }
        block(vm)
    }

    @Test
    fun withoutRulesTheListIsEmptyAndNotLoading() = runVm { vm ->
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.rules.isEmpty())
    }

    @Test
    fun theListShowsEveryRuleAndTheAccountNames() = runVm { vm ->
        rules.rules.value = listOf(rule("Keells Super"), rule("Dialog", category = "bills", account = bank.id))

        val state = vm.uiState.value
        assertEquals(listOf("Dialog", "Keells Super"), state.rules.map { it.displayName })
        assertEquals("Bank", state.accountName(bank.id))
    }

    @Test
    fun theSwitchTurnsARuleOffWithoutDeletingItAndRaisesTheVersion() = runVm { vm ->
        rules.rules.value = listOf(rule("Keells Super"))
        vm.setEnabled(rules.rules.value.single(), false)

        val only = rules.rules.value.single()
        assertFalse(only.enabled)
        assertEquals(2, only.version)
    }

    @Test
    fun theEditorStartsFromTheRuleAndSavingChangesItOnce() = runVm { vm ->
        rules.rules.value = listOf(rule("Keells Super", category = "groceries"))
        vm.open(rules.rules.value.single())
        assertEquals("groceries", vm.uiState.value.draft?.categoryId)

        vm.onCategorySelect("coffee-dining")
        vm.onAccountSelect(cash.id)
        vm.saveEdit()

        val only = rules.rules.value.single()
        assertEquals("coffee-dining", only.categoryId)
        assertEquals(cash.id, only.accountId)
        assertEquals(2, only.version)
        assertNull(vm.uiState.value.editing)
    }

    @Test
    fun closingTheEditorWithoutSavingChangesNothing() = runVm { vm ->
        rules.rules.value = listOf(rule("Keells Super"))
        vm.open(rules.rules.value.single())
        vm.onCategorySelect("other")
        vm.closeEditor()

        assertNull(vm.uiState.value.editing)
        assertEquals("groceries", rules.rules.value.single().categoryId)
        assertEquals(0, rules.saves)
    }

    @Test
    fun savingWithoutAChangeKeepsTheVersion() = runVm { vm ->
        rules.rules.value = listOf(rule("Keells Super", version = 3))
        vm.open(rules.rules.value.single())
        vm.saveEdit()

        assertEquals(3, rules.rules.value.single().version)
        assertEquals(0, rules.saves)
    }

    @Test
    fun aFailedSaveKeepsTheEditorOpenWithAMessage() = runVm { vm ->
        rules.rules.value = listOf(rule("Keells Super"))
        vm.open(rules.rules.value.single())
        vm.onEnabledChange(false)
        rules.saveError = IOException("denied")
        vm.saveEdit()

        assertTrue(vm.uiState.value.failed)
        assertFalse(vm.uiState.value.isSaving)
        assertNotNull(vm.uiState.value.editing)
        assertEquals(1, rules.rules.value.single().version)
    }
}
