package com.example.zar.core.presentation.privacy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.zar.core.domain.repository.AmountPrivacyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The one place that decides whether amounts are shown.
 *
 * Every amount the app prints goes through the money formatters, and they ask this object. The
 * flag is Compose state, so any screen that has already drawn an amount redraws when it changes,
 * and a screen drawn afterwards is masked from its very first frame. Because it is a single
 * switch rather than a value each screen has to pass along, a new screen cannot forget to mask.
 *
 * The same switch covers notification text (built with the same formatters), charts and the
 * descriptions read out by screen readers.
 */
object AmountMask {
    /** What an amount shows while hidden. */
    const val HIDDEN_TEXT = "••••"

    private var hiddenState by mutableStateOf(false)

    val isHidden: Boolean get() = hiddenState

    fun set(hidden: Boolean) {
        hiddenState = hidden
    }
}

/**
 * Masks what is typed into, or already filled into, an amount field while amounts are hidden, so
 * an edit screen does not show a balance either. The digits still work: only the display changes.
 */
fun amountFieldTransformation(): VisualTransformation =
    if (AmountMask.isHidden) PasswordVisualTransformation('•') else VisualTransformation.None

/**
 * Applies the saved choice before anything is drawn, then follows later changes. Call it once from
 * the application class: the first read is immediate, never asynchronous.
 */
fun CoroutineScope.syncAmountMask(privacy: AmountPrivacyRepository) {
    AmountMask.set(privacy.isHidden())
    launch { privacy.observeHidden().collect { AmountMask.set(it) } }
}
