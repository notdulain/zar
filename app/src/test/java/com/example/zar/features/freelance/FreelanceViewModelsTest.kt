package com.example.zar.features.freelance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.freelance.domain.FreelanceProblem
import com.example.zar.features.freelance.domain.model.Project
import com.example.zar.features.freelance.domain.repository.ProjectRepository
import com.example.zar.features.freelance.domain.usecase.ObserveFreelanceUseCase
import com.example.zar.features.freelance.domain.usecase.SaveProjectUseCase
import com.example.zar.features.freelance.presentation.viewmodel.FreelanceMessage
import com.example.zar.features.freelance.presentation.viewmodel.FreelanceViewModel
import com.example.zar.features.freelance.presentation.viewmodel.ProjectFormViewModel
import com.example.zar.features.reminders.domain.ObserveOverdueMilestoneAlertsUseCase
import com.example.zar.features.reminders.domain.PaymentReminderScheduler
import com.example.zar.features.reminders.domain.ReminderPreferences
import com.example.zar.features.reminders.domain.ReminderPreferencesRepository
import com.example.zar.features.reminders.domain.SetOverdueMilestoneAlertsUseCase
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

/** L10: Freelance screens when empty, when loading fails, and when a save fails offline. */
@OptIn(ExperimentalCoroutinesApi::class)
class FreelanceViewModelsTest {

    private class Projects(private val failLoad: Boolean = false) : ProjectRepository {
        val projects = MutableStateFlow<List<Project>>(emptyList())
        var offline = false
        val attemptedIds = mutableListOf<com.example.zar.features.freelance.domain.model.ProjectId>()
        override fun observeProjects(): Flow<List<Project>> = if (failLoad) flow { throw IOException("Permission denied") } else projects
        override suspend fun saveProject(project: Project) {
            attemptedIds += project.id
            if (offline) throw IOException("Offline")
            projects.value = projects.value.filterNot { it.id == project.id } + project
        }
    }

    private class Preferences : ReminderPreferencesRepository {
        val prefs = MutableStateFlow(ReminderPreferences())
        override fun observe(): Flow<ReminderPreferences> = prefs
        override suspend fun markNotified(keys: Set<String>, today: LocalDate) = Unit
        override suspend fun setOverdueMilestoneAlerts(enabled: Boolean) { prefs.value = prefs.value.copy(overdueMilestoneAlerts = enabled) }
        override suspend fun clear() = Unit
    }

    private class Scheduler : PaymentReminderScheduler {
        var checks = 0
        override fun ensureScheduled() = Unit
        override fun checkNow() { checks++ }
        override fun cancel() = Unit
    }

    private val projects = Projects()
    private val preferences = Preferences()
    private val scheduler = Scheduler()
    private fun observe(repo: ProjectRepository = projects) = ObserveFreelanceUseCase(repo, FakeExpectedPaymentRepository(), FakeLedgerRepository())

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun list(repo: ProjectRepository = projects) = FreelanceViewModel(
        observe(repo),
        ObserveOverdueMilestoneAlertsUseCase(preferences),
        SetOverdueMilestoneAlertsUseCase(preferences, scheduler),
    )

    @Test
    fun listShowsEmptyOnceLoaded() = runTest {
        val vm = list()
        assertTrue(vm.uiState.value.isLoading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.projects.isEmpty())
        assertFalse(vm.uiState.value.loadFailed)
        vm.viewModelScope.cancel()
    }

    @Test
    fun listSaysLoadingFailed() = runTest {
        val vm = list(Projects(failLoad = true))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        assertTrue(vm.uiState.value.loadFailed)
        assertNull(vm.uiState.value.outstanding)
        vm.viewModelScope.cancel()
    }

    @Test
    fun overdueAlertsAreOnByDefaultAndTurningThemOnChecksNow() = runTest {
        val vm = list()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.overdueAlerts.collect { } }
        assertTrue(vm.overdueAlerts.value)
        vm.setOverdueAlerts(false)
        assertFalse(vm.overdueAlerts.value)
        assertEquals("Turning off doesn't trigger a check", 0, scheduler.checks)
        vm.setOverdueAlerts(true)
        assertEquals(1, scheduler.checks)
        vm.viewModelScope.cancel()
    }

    private fun form(projectId: String? = null) =
        ProjectFormViewModel(SavedStateHandle(mapOf("projectId" to projectId)), observe(), SaveProjectUseCase(projects))

    @Test
    fun projectNeedsANameBeforeSaving() = runTest {
        val vm = form()
        vm.save()
        assertTrue(FreelanceProblem.NAME_MISSING in vm.uiState.value.problems)
        assertTrue(projects.projects.value.isEmpty())
        vm.viewModelScope.cancel()
    }

    @Test
    fun failedSaveKeepsTheFormAndRetryCreatesOneProject() = runTest {
        val vm = form()
        vm.onNameChange("Logo redesign")
        vm.onClientChange("Acme")
        vm.onCurrencySelect(Currencies.USD)
        projects.offline = true
        vm.save()
        assertEquals(FreelanceMessage.FAILED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.savedProjectId)
        assertEquals("Logo redesign", vm.uiState.value.name)

        projects.offline = false
        vm.save()
        vm.save()
        assertEquals("Failed attempts and retries keep the same ID", 1, projects.attemptedIds.distinct().size)
        val saved = projects.projects.value.single()
        assertEquals(Currencies.USD, saved.currency)
        assertEquals(saved.id.value, vm.uiState.value.savedProjectId)
        vm.viewModelScope.cancel()
    }

    @Test
    fun editingKeepsTheCurrencyAndAMissingProjectSaysNotFound() = runTest {
        projects.projects.value = listOf(FreelanceFixtures.project("p1", Currencies.USD))
        val vm = form("p1")
        vm.onCurrencySelect(Currencies.LKR)
        assertEquals("A project's currency can't change", Currencies.USD, vm.uiState.value.currency)
        vm.viewModelScope.cancel()

        val missing = form("gone")
        assertEquals(FreelanceMessage.NOT_FOUND, missing.uiState.value.message)
        missing.viewModelScope.cancel()
    }
}
