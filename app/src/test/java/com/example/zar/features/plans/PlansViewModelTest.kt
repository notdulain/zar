package com.example.zar.features.plans

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.domain.repository.GoalRepository
import com.example.zar.features.plans.domain.usecase.ObservePlansOverviewUseCase
import com.example.zar.features.plans.domain.usecase.SaveGoalUseCase
import com.example.zar.features.plans.domain.usecase.SetGoalAllocationUseCase
import com.example.zar.features.plans.presentation.viewmodel.PlansMessage
import com.example.zar.features.plans.presentation.viewmodel.PlansViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
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

    @Test
    fun savesGoalDateForPaceCalculation() = runTest {
        val date = LocalDate.of(2027, 6, 1)
        vm.openGoalEditor()
        vm.updateGoalDate(date)
        vm.submitGoal()
        assertEquals(date, vm.uiState.value.overview?.goal?.targetDate)
    }


    @Test
    fun removingGoalDatePreservesAllocations() = runTest {
        vm.openGoalEditor()
        vm.updateGoalDate(LocalDate.of(2027, 6, 1))
        vm.submitGoal()
        vm.openAllocationEditor()
        vm.updateAllocationAmount("1000")
        vm.submitAllocation()
        vm.openGoalEditor()
        vm.updateGoalDate(null)
        vm.submitGoal()
        assertNull(vm.uiState.value.overview?.goal?.targetDate)
        assertEquals(lkr(1_000), vm.uiState.value.overview?.goal?.allocated)
    }

    @Test
    fun createGoalWritesNothingUntilThePlansHaveLoaded() = runTest {
        val saved = mutableListOf<SavingsGoal>()
        val neverLoads = object : GoalRepository {
            override fun observeGoals(): Flow<List<SavingsGoal>> = flow { awaitCancellation() }
            override suspend fun saveGoal(goal: SavingsGoal) { saved += goal }
        }
        val accounts = FakePlanAccounts(listOf(savings))
        val unloaded = PlansViewModel(
            ObservePlansOverviewUseCase(neverLoads, accounts, TrackingPlanLedger()),
            SaveGoalUseCase(neverLoads),
            SetGoalAllocationUseCase(neverLoads, accounts, TrackingPlanLedger()),
        )
        unloaded.createGoal("Laptop", "490000", null)
        assertTrue(unloaded.uiState.value.overview == null)
        assertTrue(saved.isEmpty())
        assertEquals(0, unloaded.uiState.value.goalCreatedCount)
    }

    @Test
    fun createGoalNeverReplacesAnExistingGoal() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.createGoal("Laptop", "490000", null)
        assertEquals("Laptop", vm.uiState.value.overview?.goal?.name)
        vm.createGoal("Other", "1000", null)
        assertEquals("Laptop", vm.uiState.value.overview?.goal?.name)
        assertEquals(1, vm.uiState.value.goalCreatedCount)
    }
}
