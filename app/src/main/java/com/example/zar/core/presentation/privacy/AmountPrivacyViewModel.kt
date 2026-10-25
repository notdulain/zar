package com.example.zar.core.presentation.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.usecase.ObserveAmountsHiddenUseCase
import com.example.zar.core.domain.usecase.SetAmountsHiddenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The hide-amounts control shown on Home and in Settings. */
@HiltViewModel
class AmountPrivacyViewModel @Inject constructor(
    private val observeHidden: ObserveAmountsHiddenUseCase,
    private val setHidden: SetAmountsHiddenUseCase,
) : ViewModel() {

    /** Starts from the saved value, so the control never flashes the wrong state. */
    val hidden: StateFlow<Boolean> = observeHidden()
        .stateIn(viewModelScope, SharingStarted.Eagerly, observeHidden.current())

    fun toggle() = set(!hidden.value)

    fun set(hidden: Boolean) {
        viewModelScope.launch { setHidden(hidden) }
    }
}
