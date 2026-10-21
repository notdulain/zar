package com.example.zar.features.catchup.presentation

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.catchup.FakeCatchUpPreferences
import com.example.zar.features.catchup.FakeReminderScheduler
import com.example.zar.features.catchup.domain.model.CatchUpPreferences
import com.example.zar.features.catchup.domain.model.ReminderSettings
import com.example.zar.features.catchup.domain.usecase.ObserveCatchUpPreferencesUseCase
import com.example.zar.features.catchup.domain.usecase.ObserveCatchUpUseCase
import com.example.zar.features.catchup.domain.usecase.UpdateReminderSettingsUseCase
import com.example.zar.features.catchup.presentation.viewmodel.NeedsReviewViewModel
import com.example.zar.features.catchup.presentation.viewmodel.ReminderSettingsViewModel
import com.example.zar.features.transactions.presentation.activity.FakeAccounts
import java.time.DayOfWeek
import java.time.LocalTime
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
class ReminderSettingsViewModelTest {
    private val scheduler = FakeReminderScheduler()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun run(initial: CatchUpPreferences = CatchUpPreferences(), block: suspend (ReminderSettingsViewModel) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val store = FakeCatchUpPreferences(initial)
        val vm = ReminderSettingsViewModel(ObserveCatchUpPreferencesUseCase(store), UpdateReminderSettingsUseCase(store, scheduler))
        backgroundScope.launch { vm.uiState.collect { } }
        block(vm)
    }


    @Test
    fun theDefaultIsSundayAtSevenPmWithANextReminderAndNothingSkipped() = run { vm ->
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.settings.enabled)
        assertEquals(DayOfWeek.SUNDAY, state.settings.day)
        assertEquals(LocalTime.of(19, 0), state.settings.time)
        assertEquals(DayOfWeek.SUNDAY, state.nextReminder!!.dayOfWeek)
        assertEquals(LocalTime.of(19, 0), state.nextReminder!!.toLocalTime())
        assertFalse(state.isNextSkipped)
    }

    @Test
    fun movingTheReminderSavesItAndArrangesTheNewTime() = run { vm ->
        vm.onMove(DayOfWeek.FRIDAY, LocalTime.of(20, 0))

        val state = vm.uiState.value
        assertEquals(DayOfWeek.FRIDAY, state.settings.day)
        assertEquals(LocalTime.of(20, 0), state.settings.time)
        assertEquals(DayOfWeek.FRIDAY, state.nextReminder!!.dayOfWeek)
        assertEquals(LocalTime.of(20, 0), scheduler.last!!.toLocalTime())
    }

    @Test
    fun skippingThisWeekMovesTheNextReminderAndUndoingBringsItBack() = run { vm ->
        val before = vm.uiState.value.nextReminder!!

        vm.skipThisWeek()
        val skipped = vm.uiState.value
        assertTrue(skipped.isNextSkipped)
        assertEquals(before.plusWeeks(1), skipped.nextReminder)

        vm.undoSkip()
        val restored = vm.uiState.value
        assertFalse(restored.isNextSkipped)
        assertEquals(before, restored.nextReminder)
    }

    @Test
    fun movingTheReminderEndsAnEarlierSkip() = run { vm ->
        vm.skipThisWeek()
        assertNotNull(vm.uiState.value.settings.skippedOccurrence)

        vm.onMove(DayOfWeek.SATURDAY, LocalTime.of(18, 0))

        assertNull(vm.uiState.value.settings.skippedOccurrence)
        assertFalse(vm.uiState.value.isNextSkipped)
    }

    @Test
    fun turningTheReminderOffClearsTheNextTimeAndCancelsIt() = run { vm ->
        vm.onEnabledChange(false)

        val state = vm.uiState.value
        assertFalse(state.settings.enabled)
        assertNull(state.nextReminder)
        assertNull(scheduler.last)
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun turningItBackOnArrangesTheReminderAgain() = run(CatchUpPreferences(reminder = ReminderSettings(enabled = false))) { vm ->
        vm.onEnabledChange(true)

        assertTrue(vm.uiState.value.settings.enabled)
        assertNotNull(vm.uiState.value.nextReminder)
        assertNotNull(scheduler.last)
    }

    @Test
    fun savedSettingsAreShownAsTheyWere() = run(CatchUpPreferences(reminder = ReminderSettings(day = DayOfWeek.MONDAY, time = LocalTime.of(7, 15)))) { vm ->
        assertEquals(DayOfWeek.MONDAY, vm.uiState.value.settings.day)
        assertEquals(LocalTime.of(7, 15), vm.uiState.value.settings.time)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NeedsReviewViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun itListsTheOpenItemsAndTheDaysSinceTheLastCatchUp() = runTest(UnconfinedTestDispatcher()) {
        val ledger = FakeLedgerRepository()
        ledger.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(500), cash, ZarTime.today(), SpendingClass.UNKNOWN))
        val prefs = FakeCatchUpPreferences(CatchUpPreferences(lastCatchUp = ZarTime.today().minusDays(9)))
        val vm = NeedsReviewViewModel(ObserveCatchUpUseCase(ledger, FakeAccounts(listOf(cash)), prefs))
        backgroundScope.launch { vm.uiState.collect { } }

        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertEquals(listOf("e1"), state.items.map { it.entryId.value })
        assertEquals(9, state.daysSinceCatchUp)
    }

    @Test
    fun withNothingOpenAndNoCatchUpYetItIsEmptyWithNoDate() = runTest(UnconfinedTestDispatcher()) {
        val vm = NeedsReviewViewModel(ObserveCatchUpUseCase(FakeLedgerRepository(), FakeAccounts(listOf(cash)), FakeCatchUpPreferences()))
        backgroundScope.launch { vm.uiState.collect { } }

        assertTrue(vm.uiState.value.items.isEmpty())
        assertNull(vm.uiState.value.daysSinceCatchUp)
    }
}
