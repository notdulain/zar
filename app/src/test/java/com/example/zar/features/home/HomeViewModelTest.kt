package com.example.zar.features.home

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.freelance.FreelanceFixtures
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

    private fun viewModel() = HomeViewModel(rig.overview, rig.commitments)

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
}
