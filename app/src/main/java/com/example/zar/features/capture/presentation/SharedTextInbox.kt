package com.example.zar.features.capture.presentation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds text shared to Zar from another app until the capture screen reads it. Memory only:
 * the text is never written to disk, saved state or Firestore, and is cleared once taken.
 */
@Singleton
class SharedTextInbox @Inject constructor() {
    private val pending = MutableStateFlow<String?>(null)

    /** True while shared text is waiting to be read. */
    val hasText: StateFlow<Boolean> get() = _hasText.asStateFlow()
    private val _hasText = MutableStateFlow(false)

    fun offer(text: String) {
        if (text.isBlank()) return
        pending.value = text
        _hasText.value = true
    }

    /** Returns the waiting text once, then forgets it. */
    fun take(): String? {
        val text = pending.value
        pending.value = null
        _hasText.value = false
        return text
    }
}
