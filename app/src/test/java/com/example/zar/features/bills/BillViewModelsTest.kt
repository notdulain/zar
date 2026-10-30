package com.example.zar.features.bills

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.FakeAccountRepository
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.features.bills.domain.BillProblem
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillCancellation
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillReminder
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.bills.domain.repository.BillRepository
import com.example.zar.features.bills.domain.usecase.ObserveBillAccountsUseCase
import com.example.zar.features.bills.domain.usecase.ObserveBillsUseCase
import com.example.zar.features.bills.domain.usecase.SaveBillUseCase
import com.example.zar.features.bills.domain.usecase.SetBillCancellationUseCase
import com.example.zar.features.bills.presentation.viewmodel.BillFormViewModel
import com.example.zar.features.bills.presentation.viewmodel.BillMessage
import com.example.zar.features.bills.presentation.viewmodel.BillViewModel
import com.example.zar.features.bills.presentation.viewmodel.BillsViewModel
import com.example.zar.features.reminders.domain.PaymentReminderScheduler
import com.example.zar.features.reminders.domain.RefreshRemindersUseCase
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** L10: what the Bills screens show while loading, when empty, and when loading or saving fails. */
@OptIn(ExperimentalCoroutinesApi::class)
class BillViewModelsTest {

    private class Bills(var failLoad: Boolean = false) : BillRepository {
        val bills = MutableStateFlow<List<Bill>>(emptyList())
        var offline = false
        var saves = 0
        val attemptedIds = mutableListOf<BillId>()
        override fun observeBills(): Flow<List<Bill>> = if (failLoad) flow { throw IOException("Permission denied") } else bills
        override suspend fun saveBill(bill: Bill) {
            saves++
            attemptedIds += bill.id
            if (offline) throw IOException("Offline")
            bills.value = bills.value.filterNot { it.id == bill.id } + bill
        }
    }

    private class Expected : ExpectedPaymentRepository {
        override fun observeExpectedPayments(): Flow<List<ExpectedPayment>> = MutableStateFlow(emptyList())
        override fun observeMatches(): Flow<List<PaymentMatch>> = MutableStateFlow(emptyList())
        override suspend fun saveExpectedPayment(payment: ExpectedPayment) = Unit
        override suspend fun saveMatch(match: PaymentMatch) = Unit
        override suspend fun removeMatch(expectedPaymentId: ExpectedPaymentId, transactionId: TransactionId) = Unit
    }

    private class Scheduler : PaymentReminderScheduler {
        var checks = 0
        override fun ensureScheduled() = Unit
        override fun checkNow() { checks++ }
        override fun cancel() = Unit
    }

    private val bills = Bills()
    private val accounts = FakeAccountRepository(bank)
    private val scheduler = Scheduler()
    private fun observeBills(repo: BillRepository = bills) = ObserveBillsUseCase(repo, accounts, Expected(), FakeLedgerRepository())

    private val netflix = Bill(BillId("b1"), "Netflix", bank.id, lkr(2_643), BillFrequency.MONTHLY, LocalDate.of(2026, 9, 9), BillStatus.ACTIVE, T0)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listStartsLoadingThenShowsEmpty() = runTest {
        val vm = BillsViewModel(observeBills(), ObserveBillAccountsUseCase(accounts))
        assertTrue("Loading before anything arrives", vm.uiState.value.isLoading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.loadFailed)
        assertEquals("No bills yet", false, vm.uiState.value.list?.hasBills)
        vm.viewModelScope.cancel()
    }

    @Test
    fun listSaysLoadingFailedInsteadOfShowingEmpty() = runTest {
        val vm = BillsViewModel(observeBills(Bills(failLoad = true)), ObserveBillAccountsUseCase(accounts))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        assertTrue(vm.uiState.value.loadFailed)
        assertNull("A failure isn't shown as an empty list", vm.uiState.value.list)
        vm.viewModelScope.cancel()
    }

    private fun form(billId: String? = null) = BillFormViewModel(
        SavedStateHandle(mapOf("billId" to billId)),
        ObserveBillAccountsUseCase(accounts),
        observeBills(),
        SaveBillUseCase(bills),
        RefreshRemindersUseCase(scheduler),
    )

    @Test
    fun emptyFormShowsProblemsAndSavesNothing() = runTest {
        val vm = form()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("The only account is preselected", bank.id, vm.uiState.value.accountId)
        vm.save()
        assertTrue(BillProblem.NAME_MISSING in vm.uiState.value.problems)
        assertTrue(BillProblem.AMOUNT_MISSING in vm.uiState.value.problems)
        assertEquals(0, bills.saves)
        vm.onNameChange("Netflix")
        assertFalse("Typing clears that problem", BillProblem.NAME_MISSING in vm.uiState.value.problems)
        vm.viewModelScope.cancel()
    }

    @Test
    fun failedSaveKeepsWhatWasTypedAndRetrySavesOneBill() = runTest {
        val vm = form()
        vm.onNameChange("Netflix")
        vm.onAmountChange("2643")
        vm.onReminderSelect(BillReminder.ONE_DAY_BEFORE)
        bills.offline = true
        vm.save()
        assertEquals(BillMessage.FAILED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.savedBillId)
        assertEquals("Netflix", vm.uiState.value.name)
        assertEquals("2643", vm.uiState.value.amount)
        assertEquals("No reminder refresh after a failed save", 0, scheduler.checks)

        bills.offline = false
        vm.save()
        vm.save()
        assertEquals("Retrying reuses the same ID: one bill", 1, bills.bills.value.size)
        assertEquals("Failed attempts and retries keep the same ID", 1, bills.attemptedIds.distinct().size)
        assertEquals(bills.bills.value.single().id.value, vm.uiState.value.savedBillId)
        assertEquals(BillReminder.ONE_DAY_BEFORE, bills.bills.value.single().reminder)
        assertTrue("A reminder due today is checked straight away", scheduler.checks >= 1)
        vm.viewModelScope.cancel()
    }

    @Test
    fun editingAMissingBillSaysNotFound() = runTest {
        val vm = form(billId = "gone")
        assertTrue(vm.uiState.value.notFound)
        vm.viewModelScope.cancel()
    }

    @Test
    fun editingFillsTheFormFromTheSavedBill() = runTest {
        bills.bills.value = listOf(netflix)
        val vm = form(billId = "b1")
        assertTrue(vm.uiState.value.isEditing)
        assertEquals("Netflix", vm.uiState.value.name)
        assertEquals("2643", vm.uiState.value.amount)
        vm.viewModelScope.cancel()
    }

    @Test
    fun failedCancellationShowsAMessageAndChangesNothing() = runTest {
        bills.bills.value = listOf(netflix)
        val vm = BillViewModel(
            SavedStateHandle(mapOf("billId" to "b1")),
            observeBills(), ObserveBillAccountsUseCase(accounts),
            SetBillCancellationUseCase(bills), SaveBillUseCase(bills), RefreshRemindersUseCase(scheduler),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        bills.offline = true
        vm.confirmCancelled(LocalDate.of(2026, 11, 1))
        assertEquals(BillMessage.FAILED, vm.message.value)
        assertFalse(vm.isSaving.value)
        assertNull(bills.bills.value.single().cancellation)

        bills.offline = false
        vm.confirmCancelled(LocalDate.of(2026, 11, 1))
        assertNull(vm.message.value)
        assertEquals(BillCancellation.Confirmed(LocalDate.of(2026, 11, 1)), bills.bills.value.single().cancellation)
        vm.viewModelScope.cancel()
    }
}
