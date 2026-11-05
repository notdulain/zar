package com.example.zar.features.home

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.freelance.FreelanceFixtures
import com.example.zar.features.home.presentation.viewmodel.HomeAllowanceContent
import com.example.zar.features.home.presentation.viewmodel.HomeCommitmentsContent
import com.example.zar.features.home.presentation.viewmodel.HomeContent
import com.example.zar.features.home.presentation.viewmodel.HomeViewModel
import java.io.IOException
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.home.presentation.viewmodel.HomeGoalContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val rig = HomeRig()
    private val ledger = rig.ledger
    private val thisMonth = YearMonth.from(ZarTime.today())

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(rig.overview, rig.commitments, rig.allowance, rig.plans, rig.detailRequests)

    @Test
    fun startsOnTheCurrentMonthWithTotalsFromSavedRecords() = runTest {
        ledger.entries.value = LedgerProjector.project(
            listOf(tx("pay", TransactionType.INCOME, lkr(5_000), date = ZarTime.today())),
            emptyList(),
            emptyList(),
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertEquals(thisMonth, state.month)
        assertFalse(state.canGoForward)
        val ready = state.content as HomeContent.Ready
        assertEquals(lkr(5_000), ready.overview.summary.received.amount)
    }

    @Test
    fun canStepBackButNeverIntoTheFuture() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.showNextMonth()
        assertEquals(thisMonth, vm.uiState.value.month)

        vm.showPreviousMonth()
        assertEquals(thisMonth.minusMonths(1), vm.uiState.value.month)
        assertTrue(vm.uiState.value.canGoForward)

        vm.showNextMonth()
        assertEquals(thisMonth, vm.uiState.value.month)
    }

    @Test
    fun monthQueriesKeepTheDashboardVisibleAndRapidChangesUseTheLatestMonth() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val shown = vm.uiState.value
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        ledger.source = flow { gate.await(); emit(emptyList()) }

        vm.showPreviousMonth()
        assertEquals(shown, vm.uiState.value)
        vm.showPreviousMonth()
        assertEquals(shown, vm.uiState.value)
        gate.complete(Unit)
        assertEquals(thisMonth.minusMonths(2), vm.uiState.value.month)
        assertTrue(vm.uiState.value.content is HomeContent.Ready)
    }

    @Test
    fun loadFailureShowsErrorAndRetryRecovers() = runTest {
        ledger.source = flow { throw IOException("offline cache unavailable") }
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(HomeContent.Error, vm.uiState.value.content)

        ledger.source = null
        vm.retry()
        assertTrue(vm.uiState.value.content is HomeContent.Ready)
    }

    @Test
    fun totalsFollowNewRecordsWhileTheScreenIsOpen() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue((vm.uiState.value.content as HomeContent.Ready).overview.summary.received.amount.isZero)

        ledger.entries.value = LedgerProjector.project(
            listOf(tx("late", TransactionType.INCOME, lkr(750), date = ZarTime.today())),
            emptyList(),
            emptyList(),
        )
        assertEquals(lkr(750), (vm.uiState.value.content as HomeContent.Ready).overview.summary.received.amount)
    }

    @Test
    fun clientsOweAndNextBillArriveAlongsideTheTotals() = runTest {
        rig.projects.projects.value = listOf(FreelanceFixtures.project("p1", Currencies.USD))
        rig.expected.payments.value = listOf(FreelanceFixtures.milestone("m1", "p1", com.example.zar.core.domain.TestLedger.usd(900)))
        val dueIn3 = ZarTime.today().plusDays(3)
        rig.bills.bills.value = listOf(
            Bill(BillId("b1"), "Dialog", com.example.zar.core.domain.TestLedger.bank.id, lkr(2_450), BillFrequency.MONTHLY, dueIn3, BillStatus.ACTIVE, com.example.zar.core.domain.TestLedger.T0),
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val ready = vm.uiState.value.commitments as HomeCommitmentsContent.Ready
        assertEquals(listOf(com.example.zar.core.domain.TestLedger.usd(900)), ready.commitments.outstanding.owed)
        assertEquals("Dialog", ready.commitments.nextBill?.name)
        assertEquals(dueIn3, ready.commitments.nextBill?.dueDate)
        assertTrue(vm.uiState.value.content is HomeContent.Ready)
    }

    @Test
    fun aFailingCommitmentsSourceNeverHidesTheTotalsAndRetryRecovers() = runTest {
        rig.projects.failing = true
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertEquals(HomeCommitmentsContent.Error, vm.uiState.value.commitments)
        assertTrue(vm.uiState.value.content is HomeContent.Ready)

        rig.projects.failing = false
        vm.retry()
        assertTrue(vm.uiState.value.commitments is HomeCommitmentsContent.Ready)
    }

    @Test
    fun theWeeklyLimitIsOffUntilTheUserSetsAnAllowance() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val ready = vm.uiState.value.allowance as HomeAllowanceContent.Ready
        assertEquals(null, ready.overview.status)
    }

    @Test
    fun theWeeklyLimitShowsWhatIsLeftThisWeekFromSavedSpending() = runTest {
        rig.allowanceSettings.settings.value = AllowanceSettings(isEnabled = true, amount = lkr(5_000))
        ledger.entries.value = LedgerProjector.project(
            listOf(tx("lunch", TransactionType.EXPENSE, lkr(1_200), date = ZarTime.today())),
            emptyList(),
            emptyList(),
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val status = (vm.uiState.value.allowance as HomeAllowanceContent.Ready).overview.status!!
        assertEquals(lkr(3_800), status.remaining)
    }

    @Test
    fun aFailingAllowanceNeverHidesTheTotalsAndRetryRecovers() = runTest {
        rig.allowanceSettings.failReads = true
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertEquals(HomeAllowanceContent.Error, vm.uiState.value.allowance)
        assertTrue(vm.uiState.value.content is HomeContent.Ready)
        assertTrue(vm.uiState.value.commitments is HomeCommitmentsContent.Ready)

        rig.allowanceSettings.failReads = false
        vm.retry()
        assertTrue(vm.uiState.value.allowance is HomeAllowanceContent.Ready)
    }

    @Test
    fun goalCardFollowsTheSelectedMainPlanNotTheFirstOrOnlyOne() = runTest {
        val a = SavingsGoal(GoalId("a"), "Alpha", lkr(1_000))
        val b = SavingsGoal(GoalId("b"), "Bravo", lkr(2_000))
        rig.goals.goals.value = listOf(a, b)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals("Alpha", (vm.uiState.value.goal as HomeGoalContent.Ready).goal.name)

        rig.planPreferences.mainGoalId.value = GoalId("b")
        assertEquals("Bravo", (vm.uiState.value.goal as HomeGoalContent.Ready).goal.name)

        // Creating another plan never changes the saved main plan.
        rig.goals.goals.value = rig.goals.goals.value + SavingsGoal(GoalId("c"), "Charlie", lkr(500))
        assertEquals("Bravo", (vm.uiState.value.goal as HomeGoalContent.Ready).goal.name)
    }

    @Test
    fun goalCardIsEmptyWithoutPlansAndOpeningAPlanAsksPlansToShowIt() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(HomeGoalContent.Empty, vm.uiState.value.goal)

        vm.openGoal(GoalId("b"))
        assertEquals(GoalId("b"), rig.detailRequests.pending.value)
        rig.detailRequests.consume(GoalId("other"))
        assertEquals(GoalId("b"), rig.detailRequests.pending.value)
        rig.detailRequests.consume(GoalId("b"))
        assertEquals(null, rig.detailRequests.pending.value)
    }

    @Test
    fun goalFailureShowsAnErrorWithoutHidingTheTotalsAndRetryRecovers() = runTest {
        rig.goals.failing = true
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(HomeGoalContent.Error, vm.uiState.value.goal)
        assertTrue(vm.uiState.value.content is HomeContent.Ready)

        rig.goals.failing = false
        vm.retry()
        assertEquals(HomeGoalContent.Empty, vm.uiState.value.goal)
    }
}
