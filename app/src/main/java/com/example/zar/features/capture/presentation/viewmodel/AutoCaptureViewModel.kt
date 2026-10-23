package com.example.zar.features.capture.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.capture.domain.auto.AutoCaptureStatus
import com.example.zar.features.capture.domain.auto.DismissExpiredSmsUseCase
import com.example.zar.features.capture.domain.auto.ObserveAutoCaptureUseCase
import com.example.zar.features.capture.domain.auto.SetAutoCaptureUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AutoCaptureUiState(
    val isLoading: Boolean = true,
    val status: AutoCaptureStatus = AutoCaptureStatus(),
    val isSaving: Boolean = false,
    val failed: Boolean = false,
)

@HiltViewModel
class AutoCaptureViewModel @Inject constructor(
    observe: ObserveAutoCaptureUseCase,
    private val setAutoCapture: SetAutoCaptureUseCase,
    private val dismissExpired: DismissExpiredSmsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutoCaptureUiState())
    val uiState: StateFlow<AutoCaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observe()
                .catch { _uiState.update { it.copy(isLoading = false, failed = true) } }
                .collect { status -> _uiState.update { it.copy(isLoading = false, status = status) } }
        }
    }

    fun setEnabled(enabled: Boolean) = perform { setAutoCapture(enabled) }

    fun dismissExpired() = perform { dismissExpired.invoke() }

    private fun perform(block: suspend () -> Result<Unit>) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, failed = false) }
        viewModelScope.launch {
            val result = block()
            _uiState.update { it.copy(isSaving = false, failed = result.isFailure) }
        }
    }
}
