package com.example.zar.features.export.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.export.domain.model.ExportCounts
import com.example.zar.features.export.domain.model.ExportFile
import com.example.zar.features.export.domain.model.ExportKind
import com.example.zar.features.export.domain.usecase.BuildExportUseCase
import com.example.zar.features.export.domain.usecase.ObserveExportCountsUseCase
import com.example.zar.features.export.domain.usecase.SaveExportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExportMessage { NOTHING_TO_EXPORT, BUILD_FAILED, SAVE_FAILED }

data class ExportUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val counts: ExportCounts? = null,
    /** Built and waiting for the user to pick where to save it. */
    val pending: ExportFile? = null,
    /** The picker for [pending] is already open, so a recreated screen must not open it again. */
    val pickerOpen: Boolean = false,
    val isWorking: Boolean = false,
    val message: ExportMessage? = null,
    /** The export most recently saved, for the confirmation line. */
    val saved: ExportFile? = null,
) {
    val needsPicker: Boolean get() = pending != null && !pickerOpen
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val observeCounts: ObserveExportCountsUseCase,
    private val buildExport: BuildExportUseCase,
    private val saveExport: SaveExportUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()
    private var observation: Job? = null

    init { observe() }

    fun retry() = observe()

    /** Builds the file, then the screen opens the system file picker for [ExportUiState.pending]. */
    fun export(kind: ExportKind) {
        val state = _uiState.value
        if (state.isWorking || state.pending != null) return
        if ((state.counts?.of(kind) ?: 0) == 0) {
            _uiState.update { it.copy(message = ExportMessage.NOTHING_TO_EXPORT, saved = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, message = null, saved = null) }
            buildExport(kind)
                .onSuccess { file -> _uiState.update { it.copy(pending = file, isWorking = false) } }
                .onFailure { _uiState.update { it.copy(isWorking = false, message = ExportMessage.BUILD_FAILED) } }
        }
    }

    /** The screen launched the picker for [ExportUiState.pending]. */
    fun onPickerOpened() = _uiState.update { if (it.pending != null) it.copy(pickerOpen = true) else it }

    /** The picker's answer: where to save, or null when the user backed out. */
    fun onDestinationPicked(location: String?) {
        val file = _uiState.value.pending ?: return
        if (location == null) {
            _uiState.update { it.copy(pending = null, pickerOpen = false) }
            return
        }
        viewModelScope.launch {
            // pickerOpen stays set until pending clears, so the screen never reopens it mid-save.
            _uiState.update { it.copy(isWorking = true, message = null) }
            saveExport(location, file)
                .onSuccess { saved ->
                    _uiState.update { it.copy(pending = null, pickerOpen = false, isWorking = false, saved = saved) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(pending = null, pickerOpen = false, isWorking = false, message = ExportMessage.SAVE_FAILED)
                    }
                }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }
            observeCounts()
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                }
                .collect { counts ->
                    _uiState.update { it.copy(isLoading = false, loadFailed = false, counts = counts) }
                }
        }
    }
}
