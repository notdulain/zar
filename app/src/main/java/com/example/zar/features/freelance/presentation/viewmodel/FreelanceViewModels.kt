package com.example.zar.features.freelance.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.money.AssetKind
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.navigation.ProjectFormRoute
import com.example.zar.core.navigation.ProjectRoute
import com.example.zar.features.freelance.domain.Checked
import com.example.zar.features.freelance.domain.FreelanceProblem
import com.example.zar.features.freelance.domain.FreelanceRules
import com.example.zar.features.freelance.domain.model.Outstanding
import com.example.zar.features.freelance.domain.model.Project
import com.example.zar.features.freelance.domain.model.ProjectId
import com.example.zar.features.freelance.domain.model.ProjectSummary
import com.example.zar.features.freelance.domain.usecase.FreelanceError
import com.example.zar.features.freelance.domain.usecase.ObserveFreelanceUseCase
import com.example.zar.features.freelance.domain.usecase.SaveProjectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FreelanceMessage { NOT_SIGNED_IN, ARCHIVED, CANCELLED, NOT_FOUND, FAILED }

internal fun Throwable.toFreelanceMessage(): FreelanceMessage = when (this) {
    is LedgerError.NotSignedIn -> FreelanceMessage.NOT_SIGNED_IN
    is FreelanceError.ProjectArchived -> FreelanceMessage.ARCHIVED
    is FreelanceError.MilestoneCancelled -> FreelanceMessage.CANCELLED
    else -> FreelanceMessage.FAILED
}

// --- Projects list -------------------------------------------------------------------------

data class FreelanceUiState(
    val isLoading: Boolean = true,
    val outstanding: Outstanding? = null,
    val projects: List<ProjectSummary> = emptyList(),
    val loadFailed: Boolean = false,
)

@HiltViewModel
class FreelanceViewModel @Inject constructor(
    observeFreelance: ObserveFreelanceUseCase,
) : ViewModel() {
    val uiState: StateFlow<FreelanceUiState> = observeFreelance(ZarTime.today())
        .map { FreelanceUiState(isLoading = false, outstanding = it.outstanding, projects = it.projects) }
        .catch { emit(FreelanceUiState(isLoading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FreelanceUiState())
}

// --- Project detail ------------------------------------------------------------------------

data class ProjectUiState(
    val isLoading: Boolean = true,
    val summary: ProjectSummary? = null,
    val loadFailed: Boolean = false,
)

@HiltViewModel
class ProjectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeFreelance: ObserveFreelanceUseCase,
) : ViewModel() {
    private val projectId = ProjectId(savedStateHandle.toRoute<ProjectRoute>().projectId)

    val uiState: StateFlow<ProjectUiState> = observeFreelance(ZarTime.today())
        .map { overview -> ProjectUiState(isLoading = false, summary = overview.projects.firstOrNull { it.project.id == projectId }) }
        .catch { emit(ProjectUiState(isLoading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectUiState())
}

// --- Project form --------------------------------------------------------------------------

data class ProjectFormUiState(
    val isLoading: Boolean = true,
    val project: Project? = null,
    val name: String = "",
    val client: String = "",
    val currency: Currency = Currencies.LKR,
    val problems: Set<FreelanceProblem> = emptySet(),
    val message: FreelanceMessage? = null,
    val isSaving: Boolean = false,
    /** The saved project's ID, once saving finished. */
    val savedProjectId: String? = null,
) {
    val isEditing: Boolean get() = project != null
    val currencyChoices: List<Currency> get() = Currencies.all.filter { it.kind == AssetKind.FIAT }
}

@HiltViewModel
class ProjectFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeFreelance: ObserveFreelanceUseCase,
    private val saveProject: SaveProjectUseCase,
) : ViewModel() {
    private val editingId = savedStateHandle.toRoute<ProjectFormRoute>().projectId?.let(::ProjectId)

    // Created once and reused on retry, so a repeated save can't create two projects.
    private val newProjectId = ProjectId(RecordIds.newId())

    private val _uiState = MutableStateFlow(ProjectFormUiState())
    val uiState: StateFlow<ProjectFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (editingId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val project = runCatching { observeFreelance(ZarTime.today()).first() }.getOrNull()
                ?.projects?.firstOrNull { it.project.id == editingId }?.project
            _uiState.update {
                if (project == null) {
                    it.copy(isLoading = false, message = FreelanceMessage.NOT_FOUND)
                } else {
                    it.copy(isLoading = false, project = project, name = project.name, client = project.client.orEmpty(), currency = project.currency)
                }
            }
        }
    }

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value, problems = it.problems - FreelanceProblem.NAME_MISSING - FreelanceProblem.NAME_TOO_LONG) }

    fun onClientChange(value: String) =
        _uiState.update { it.copy(client = value, problems = it.problems - FreelanceProblem.CLIENT_TOO_LONG) }

    fun onCurrencySelect(value: Currency) = _uiState.update { if (it.isEditing) it else it.copy(currency = value) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        when (val checked = FreelanceRules.checkProject(state.name, state.client)) {
            is Checked.Invalid -> _uiState.update { it.copy(problems = checked.problems) }
            is Checked.Valid -> viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, message = null) }
                val project = state.project
                val result = if (project != null) {
                    saveProject.update(project, checked.value)
                } else {
                    saveProject.create(newProjectId, checked.value, state.currency, Instant.now())
                }
                val id = project?.id ?: newProjectId
                result.fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, savedProjectId = id.value) } },
                    onFailure = { e -> _uiState.update { it.copy(isSaving = false, message = e.toFreelanceMessage()) } },
                )
            }
        }
    }

    fun setArchived(archived: Boolean) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            saveProject.setArchived(project, archived).fold(
                onSuccess = { _uiState.update { it.copy(isSaving = false, savedProjectId = project.id.value) } },
                onFailure = { e -> _uiState.update { it.copy(isSaving = false, message = e.toFreelanceMessage()) } },
            )
        }
    }
}
