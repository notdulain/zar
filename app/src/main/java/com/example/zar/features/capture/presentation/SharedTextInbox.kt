package com.example.zar.features.capture.presentation

import com.example.zar.features.auth.domain.repository.SignOutHandler
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
class SharedTextInbox @Inject constructor() : SignOutHandler {
    private val pending = java.util.ArrayDeque<String>()

    /** True while shared text is waiting to be read. */
    val hasText: StateFlow<Boolean> get() = _hasText.asStateFlow()
    private val _hasText = MutableStateFlow(false)

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    @Synchronized
    fun offer(text: String) {
        if (text.isBlank()) return
        if (text.length > 16_384) return
        pending.addLast(text)
        _hasText.value = true
        _revision.value += 1
    }

    override suspend fun onSignOut() = clear()

    @Synchronized
    private fun clear() { pending.clear(); _hasText.value = false }

    /** Returns the waiting text once, then forgets it. */
    @Synchronized
    fun take(): String? {
        val text = pending.pollFirst()
        _hasText.value = pending.isNotEmpty()
        return text
    }
}
