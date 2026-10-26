package com.example.zar.core.presentation.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlin.math.floor

/**
 * Owns the one selected-capsule mover for the full navbar. It is created once per navigation session (beside
 * [NavSession]) and passed down to every navbar composition, so the capsule's position survives a screen being
 * created again. The navbar only reports the measured tab slots and the current destination; this class decides
 * where the capsule is drawn.
 *
 * [presentationIndex] is a continuous tab position (0 = first slot, 3 = last). The Compose layer animates it toward
 * [targetIndex] with the shared `navSelection` spring; because it is a snapshot state, a moving capsule invalidates
 * only the navbar's draw phase, not the whole screen. [capsuleBounds] interpolates the measured slots, so a
 * selection never flies from Home or x = 0 on first reveal and RTL mirrors automatically from the real layout.
 */
@Stable
class NavCapsuleController {

    /** The destination the capsule should settle on, as a slot index. */
    var targetIndex by mutableIntStateOf(0)
        private set

    /** The continuous slot position drawn right now. Written by the animator, read by the navbar's draw phase. */
    var presentationIndex by mutableFloatStateOf(0f)
        private set

    /** False until the tab slots have been measured once, so the first reveal can snap instead of flying. */
    var revealed by mutableStateOf(false)
        private set

    private var slots: List<Rect> = emptyList()

    val slotCount: Int get() = slots.size

    /** Reports the measured tab slots in the navbar's own coordinate space (physical order, so RTL is already applied). */
    fun onSlotsMeasured(measured: List<Rect>) {
        if (measured.isEmpty()) return
        slots = measured
        val last = (measured.size - 1).toFloat()
        presentationIndex = if (revealed) presentationIndex.coerceIn(0f, last) else targetIndex.toFloat().coerceIn(0f, last)
        revealed = true
    }

    /** A new valid destination was selected. The capsule retargets from wherever it currently is. */
    fun select(index: Int) {
        targetIndex = if (slots.isEmpty()) index else index.coerceIn(0, slots.size - 1)
    }

    /** Publishes the animator's current value for the draw phase. */
    fun setPresentation(index: Float) {
        presentationIndex = index
    }

    /** Snaps the presentation to the target, for reduced motion and disabled system animations. */
    fun snapToTarget() {
        presentationIndex = targetIndex.toFloat()
    }

    /** Where the capsule is drawn right now, or null before the first measurement. */
    fun capsuleBounds(): Rect? {
        if (slots.isEmpty()) return null
        val last = (slots.size - 1).toFloat()
        val t = presentationIndex.coerceIn(0f, last)
        val lower = floor(t).toInt().coerceIn(0, slots.size - 1)
        val upper = (lower + 1).coerceAtMost(slots.size - 1)
        val fraction = (t - lower).coerceIn(0f, 1f)
        val a = slots[lower]
        val b = slots[upper]
        return Rect(
            left = a.left + (b.left - a.left) * fraction,
            top = a.top + (b.top - a.top) * fraction,
            right = a.right + (b.right - a.right) * fraction,
            bottom = a.bottom + (b.bottom - a.bottom) * fraction,
        )
    }
}
