package com.example.zar.features.allowance

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.usecase.ObserveAllowanceUseCase
import com.example.zar.features.allowance.domain.usecase.SaveAllowanceSettingsUseCase
import com.example.zar.features.allowance.presentation.viewmodel.AllowanceMessage
import com.example.zar.features.allowance.presentation.viewmodel.AllowanceViewModel
import com.example.zar.features.freelance.FakeExpectedPaymentRepository
import com.example.zar.features.home.StaticLedgerRepository
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class AllowanceViewModelTest {

    private val repo = FakeAllowanceRepository()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AllowanceViewModel(
        ObserveAllowanceUseCase(repo, StaticLedgerRepository(), FakeExpectedPaymentRepository()),
        SaveAllowanceSettingsUseCase(repo),
    )

    @Test
    fun startsOffAndLoaded() = runTest {
        val vm = viewModel()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertNull(state.overview!!.status)
        assertNull(state.editor)
    }

    @Test
    fun aFirstSetUpOpensSwitchedOnWithMondayAndNoCategories() = runTest {
        val vm = viewModel()
        vm.openEditor()
        val editor = vm.uiState.value.editor!!
        assertTrue(editor.enabled)
        assertEquals("", editor.amount)
        assertTrue(editor.categoryIds.isEmpty())
        assertEquals(DayOfWeek.MONDAY, editor.weekStart)
    }

    @Test
    fun savingAValidAllowanceStoresItAndClosesTheForm() = runTest {
        val vm = viewModel()
        vm.openEditor()
        vm.updateAmount("8,000")
        vm.toggleCategory("coffee-dining")
        vm.toggleCategory("groceries")
        vm.toggleCategory("groceries")
        vm.setWeekStart(DayOfWeek.SUNDAY)
        vm.submit()
        advanceUntilIdle()

        val saved = repo.settings.value
        assertTrue(saved.isEnabled)
        assertEquals(lkr(8_000), saved.amount)
        assertEquals(setOf("coffee-dining"), saved.categoryIds)
        assertEquals(DayOfWeek.SUNDAY, saved.weekStart)
        assertNull(vm.uiState.value.editor)
        assertNotNull(vm.uiState.value.overview!!.status)
    }

    @Test
    fun anAmountThatIsMissingZeroOrNotANumberIsRejected() = runTest {
        val vm = viewModel()
        vm.openEditor()
        listOf("", "0", "abc", "-5").forEach { typed ->
            vm.updateAmount(typed)
            vm.submit()
            advanceUntilIdle()
            assertEquals("for \"$typed\"", AllowanceMessage.INVALID_AMOUNT, vm.uiState.value.message)
        }
        assertEquals(0, repo.saveCount)
        assertNotNull(vm.uiState.value.editor)
    }

    @Test
    fun editingClearsTheErrorMessage() = runTest {
        val vm = viewModel()
        vm.openEditor()
        vm.submit()
        assertEquals(AllowanceMessage.INVALID_AMOUNT, vm.uiState.value.message)
        vm.updateAmount("100")
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun switchingItOffSavesWithoutNeedingAnAmount() = runTest {
        repo.settings.value = AllowanceSettings(true, lkr(5_000), setOf("groceries"), DayOfWeek.TUESDAY)
        val vm = viewModel()
        vm.openEditor()
        assertEquals("5000", vm.uiState.value.editor!!.amount)
        vm.setEnabled(false)
        vm.updateAmount("")
        vm.submit()
        advanceUntilIdle()

        assertFalse(repo.settings.value.isEnabled)
        assertNull(vm.uiState.value.overview!!.status)
        assertNull(vm.uiState.value.editor)
    }

    @Test
    fun anOnAllowanceStillNeedsAnAmountEvenIfItWasSavedBefore() = runTest {
        val vm = viewModel()
        vm.openEditor()
        vm.setEnabled(true)
        vm.updateAmount("  ")
        vm.submit()
        assertEquals(AllowanceMessage.INVALID_AMOUNT, vm.uiState.value.message)
    }

    @Test
    fun aFailedSaveKeepsTheFormOpenWithAMessage() = runTest {
        repo.failSaves = true
        val vm = viewModel()
        vm.openEditor()
        vm.updateAmount("100")
        vm.submit()
        advanceUntilIdle()

        assertEquals(AllowanceMessage.SAVE_FAILED, vm.uiState.value.message)
        assertNotNull(vm.uiState.value.editor)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun aLoadFailureShowsTheErrorAndRetryRecovers() = runTest {
        repo.failReads = true
        val vm = viewModel()
        assertTrue(vm.uiState.value.loadFailed)

        repo.failReads = false
        vm.retry()
        assertFalse(vm.uiState.value.loadFailed)
        assertNotNull(vm.uiState.value.overview)
    }
}
