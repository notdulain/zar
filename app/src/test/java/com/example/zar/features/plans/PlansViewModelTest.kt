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
import com.example.zar.features.plans.domain.usecase.SetMainGoalUseCase
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
import com.example.zar.features.plans.domain.model.GoalAllocation
import com.example.zar.features.plans.domain.model.GoalId
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
    private lateinit var prefs: FakePlanPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        goals = FakeGoalRepository()
        val accounts = FakePlanAccounts(listOf(savings))
        val ledger = TrackingPlanLedger()
        prefs = FakePlanPreferences()
        vm = PlansViewModel(
            ObservePlansOverviewUseCase(goals, accounts, ledger, prefs),
            SaveGoalUseCase(goals),
            SetGoalAllocationUseCase(goals, accounts, ledger),
            SetMainGoalUseCase(goals, prefs),
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
            ObservePlansOverviewUseCase(neverLoads, accounts, TrackingPlanLedger(), FakePlanPreferences()),
            SaveGoalUseCase(neverLoads),
            SetGoalAllocationUseCase(neverLoads, accounts, TrackingPlanLedger()),
            SetMainGoalUseCase(neverLoads, FakePlanPreferences()),
        )
        unloaded.createGoal("Laptop", "490000", null)
        assertTrue(unloaded.uiState.value.overview == null)
        assertTrue(saved.isEmpty())
        assertEquals(0, unloaded.uiState.value.goalCreatedCount)
    }

    @Test
    fun createGoalAddsAnotherPlanUnderItsOwnIdAndLeavesTheFirstUntouched() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.createGoal("Laptop", "490000", null)
        val first = goals.goals.value.single()
        vm.createGoal("Phone", "1000", null)

        assertEquals(2, goals.goals.value.size)
        assertEquals(first, goals.goals.value.single { it.id == first.id })
        assertEquals(2, goals.goals.value.map { it.id }.distinct().size)
        assertEquals(2, vm.uiState.value.overview?.goals?.size)
        assertEquals(2, vm.uiState.value.goalCreatedCount)
    }

    @Test
    fun aRetriedCreateReusesTheSameIdSoNoDuplicateIsAdded() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val failing = object : GoalRepository {
            var fail = true
            val saved = mutableListOf<SavingsGoal>()
            override fun observeGoals(): Flow<List<SavingsGoal>> = goals.observeGoals()
            override suspend fun saveGoal(goal: SavingsGoal) {
                saved += goal
                if (fail) throw IllegalStateException("rejected")
                goals.saveGoal(goal)
            }
        }
        val accounts = FakePlanAccounts(listOf(savings))
        val retrying = PlansViewModel(
            ObservePlansOverviewUseCase(goals, accounts, TrackingPlanLedger(), prefs),
            SaveGoalUseCase(failing),
            SetGoalAllocationUseCase(goals, accounts, TrackingPlanLedger()),
            SetMainGoalUseCase(goals, prefs),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { retrying.uiState.collect {} }
        retrying.createGoal("Laptop", "490000", null)
        assertEquals(PlansMessage.SAVE_FAILED, retrying.uiState.value.message)
        failing.fail = false
        retrying.createGoal("Laptop", "490000", null)

        assertEquals(1, failing.saved.map { it.id }.distinct().size)
        assertEquals(1, goals.goals.value.size)
    }

    @Test
    fun theLegacyMacbookGoalCoexistsWithNewPlansAndStaysMain() = runTest {
        val legacy = SavingsGoal(GoalId("macbook-fund"), "MacBook fund", lkr(490_000), listOf(GoalAllocation(savings.id, lkr(1_000))))
        goals.goals.value = listOf(legacy)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.createGoal("Aardvark", "5000", null)

        val overview = vm.uiState.value.overview!!
        assertEquals(2, overview.goals.size)
        assertEquals(legacy, goals.goals.value.single { it.id == legacy.id })
        assertEquals(legacy.id, overview.goal?.id)
    }

    @Test
    fun selectingAMainPlanMovesThePointerAndNeverChangesAnyPlan() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.createGoal("Laptop", "490000", null)
        vm.createGoal("Phone", "1000", null)
        val before = goals.goals.value.toSet()
        val laptop = goals.goals.value.single { it.name == "Laptop" }
        val phone = goals.goals.value.single { it.name == "Phone" }
        assertEquals(laptop.id, vm.uiState.value.overview?.mainGoalId)

        vm.selectMainGoal(phone.id)
        assertEquals(phone.id, vm.uiState.value.overview?.mainGoalId)
        assertEquals(phone.id, vm.uiState.value.overview?.goal?.id)
        assertEquals(before, goals.goals.value.toSet())
        assertEquals(2, vm.uiState.value.overview?.goals?.size)
    }

    @Test
    fun theFirstPlanBecomesMainExplicitlyAndLaterPlansDoNotMoveIt() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.createGoal("Zebra fund", "1000", null)
        val zebra = goals.goals.value.single()
        assertEquals(zebra.id, prefs.mainGoalId.value)
        vm.createGoal("Aardvark fund", "1000", null)
        assertEquals(zebra.id, vm.uiState.value.overview?.mainGoalId)
        assertEquals(1, prefs.writeCount)
    }

    @Test
    fun aPointerAtAMissingPlanFallsBackWithoutHidingAnyPlan() = runTest {
        prefs.mainGoalId.value = GoalId("gone")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.createGoal("Phone", "1000", null)
        assertEquals(1, vm.uiState.value.overview?.goals?.size)
        assertNotNull(vm.uiState.value.overview?.goal)
    }

    @Test
    fun anUnknownGoalCannotBeSelectedAsMain() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.selectMainGoal(GoalId("missing"))
        assertEquals(0, prefs.writeCount)
    }
}
