package com.example.zar.features.plans

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.plans.domain.usecase.ObservePlansOverviewUseCase
import com.example.zar.features.plans.domain.usecase.SaveGoalUseCase
import com.example.zar.features.plans.domain.usecase.SetGoalAllocationUseCase
import com.example.zar.features.plans.presentation.viewmodel.PlansMessage
import com.example.zar.features.plans.presentation.viewmodel.PlansViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
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
class PlansViewModelTest {
    private val savings = Account(
        AccountId("savings"), "Savings", AccountType.BANK, Currencies.LKR,
        OpeningBalance.Known(lkr(11_200)), LocalDate.of(2026, 10, 1),
    )
    private lateinit var goals: FakeGoalRepository
    private lateinit var vm: PlansViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        goals = FakeGoalRepository()
        val accounts = FakePlanAccounts(listOf(savings))
        val ledger = TrackingPlanLedger()
        vm = PlansViewModel(
            ObservePlansOverviewUseCase(goals, accounts, ledger),
            SaveGoalUseCase(goals),
            SetGoalAllocationUseCase(goals, accounts, ledger),
        )
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun createsGoalThenAllocatesExplicitAmount() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.overview?.goal)

        vm.openGoalEditor()
        vm.submitGoal()
        assertNotNull(vm.uiState.value.overview?.goal)
        assertEquals(lkr(0), vm.uiState.value.overview?.goal?.allocated)

        vm.openAllocationEditor()
        vm.updateAllocationAmount("11200")
        vm.submitAllocation()
        assertEquals(lkr(11_200), vm.uiState.value.overview?.goal?.allocated)
        assertNull(vm.uiState.value.allocationEditor)
    }

    @Test
    fun rejectsInvalidGoalAndOversizedAllocation() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.openGoalEditor()
        vm.updateGoalTarget("0")
        vm.submitGoal()
        assertEquals(PlansMessage.INVALID_GOAL, vm.uiState.value.message)

        vm.updateGoalTarget("490000")
        vm.submitGoal()
        vm.openAllocationEditor()
        vm.updateAllocationAmount("11201")
        vm.submitAllocation()
        assertEquals(PlansMessage.ALLOCATION_TOO_LARGE, vm.uiState.value.message)
        assertTrue(vm.uiState.value.overview!!.goal!!.allocations.isEmpty())
    }
    @Test
    fun blankAmountDoesNotRemoveAnExistingAllocation() = runTest {
        vm.openGoalEditor()
        vm.submitGoal()
        vm.openAllocationEditor()
        vm.updateAllocationAmount("100")
        vm.submitAllocation()
        vm.openAllocationEditor()
        vm.updateAllocationAmount("")
        vm.submitAllocation()
        assertEquals(PlansMessage.INVALID_AMOUNT, vm.uiState.value.message)
        assertEquals(lkr(100), vm.uiState.value.overview?.goal?.allocated)
        assertNotNull(vm.uiState.value.allocationEditor)
    }

}
