package com.example.zar.core.presentation.privacy

import com.example.zar.core.domain.repository.AmountPrivacyRepository
import com.example.zar.core.domain.usecase.ObserveAmountsHiddenUseCase
import com.example.zar.core.domain.usecase.SetAmountsHiddenUseCase
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAmountPrivacy(initial: Boolean = false) : AmountPrivacyRepository {
    private val state = MutableStateFlow(initial)
    var failing = false
    override fun isHidden(): Boolean = state.value
    override fun observeHidden(): Flow<Boolean> = state.asStateFlow()
    override suspend fun setHidden(hidden: Boolean) {
        if (failing) throw IOException("cannot save")
        state.value = hidden
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AmountPrivacyTest {

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AmountMask.set(false)
    }

    @After fun tearDown() {
        AmountMask.set(false)
        Dispatchers.resetMain()
    }

    @Test
    fun theSavedChoiceIsAppliedBeforeAnythingCollects() = runTest {
        // A scope that never runs: if the choice were loaded asynchronously, the mask would still be off.
        val idle = CoroutineScope(StandardTestDispatcher(testScheduler))
        idle.syncAmountMask(FakeAmountPrivacy(initial = true))
        assertTrue("hidden from the very first frame", AmountMask.isHidden)
        idle.coroutineContext[Job]?.cancel()
    }

    @Test
    fun aSavedShownChoiceStaysShown() = runTest {
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)).syncAmountMask(FakeAmountPrivacy(initial = false))
        assertFalse(AmountMask.isHidden)
    }

    @Test
    fun laterChangesAreFollowedAtOnce() = runTest {
        val privacy = FakeAmountPrivacy()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        scope.syncAmountMask(privacy)

        privacy.setHidden(true)
        assertTrue(AmountMask.isHidden)
        privacy.setHidden(false)
        assertFalse(AmountMask.isHidden)
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun theControlStartsFromTheSavedValueAndToggles() = runTest {
        val privacy = FakeAmountPrivacy(initial = true)
        val vm = AmountPrivacyViewModel(ObserveAmountsHiddenUseCase(privacy), SetAmountsHiddenUseCase(privacy))
        assertTrue(vm.hidden.value)

        vm.toggle()
        advanceUntilIdle()
        assertFalse(vm.hidden.value)
        assertFalse(privacy.isHidden())

        vm.set(true)
        advanceUntilIdle()
        assertTrue(privacy.isHidden())
    }

    @Test
    fun aFailedSaveDoesNotCrashAndKeepsTheOldChoice() = runTest {
        val privacy = FakeAmountPrivacy(initial = false).apply { failing = true }
        val vm = AmountPrivacyViewModel(ObserveAmountsHiddenUseCase(privacy), SetAmountsHiddenUseCase(privacy))
        vm.toggle()
        advanceUntilIdle()
        assertFalse(vm.hidden.value)
    }

    @Test
    fun theUseCasesReportTheSameValueNowAndOverTime() = runTest {
        val privacy = FakeAmountPrivacy(initial = true)
        val observe = ObserveAmountsHiddenUseCase(privacy)
        assertEquals(true, observe.current())
        assertTrue(SetAmountsHiddenUseCase(privacy)(false).isSuccess)
        assertEquals(false, observe.current())
    }
}
