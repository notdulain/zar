package com.example.zar.core.presentation.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.motion.LocalZarMotion
import kotlinx.coroutines.launch

/** Direct manipulation writes only a layer offset; child scrolling gets first use of a drag at rest. */
@Composable
internal fun Modifier.sheetSwipeToDismiss(state: SheetBackState, enabled: Boolean): Modifier {
    if (!state.swipeEnabled) return this
    val scope = state.swipeScope ?: return this
    val policy = LocalZarMotion.current
    val density = LocalDensity.current
    val entered by remember(state) { derivedStateOf { state.entrance.value == 0f && state.fade.value == 1f } }
    val active by rememberUpdatedState(enabled && entered)
    val distance = with(density) { 96.dp.toPx() }
    val flickSpeed = with(density) { 1000.dp.toPx() }
    val flickDistance = with(density) { 16.dp.toPx() }

    fun dragBy(delta: Float): Float {
        if (!active || state.closing) return 0f
        state.swipeJob?.cancel()
        val previous = state.swipeOffsetPx
        state.swipeOffsetPx = (previous + delta).coerceIn(0f, state.swipeExtentPx)
        return state.swipeOffsetPx - previous
    }
    fun release(velocity: Float) {
        if (state.closing || state.swipeOffsetPx == 0f) return
        val threshold = minOf(distance, state.swipeExtentPx * 0.25f)
        val commit = active && (
            (state.swipeOffsetPx >= threshold && velocity >= -flickSpeed / 2) ||
                (state.swipeOffsetPx >= flickDistance && velocity >= flickSpeed)
            )
        state.swipeJob?.cancel()
        if (commit) state.onSwipeDismiss?.invoke(velocity)
        else {
            state.swipeSettling = true
            state.swipeJob = scope.launch { state.settleSwipe(0f, velocity, policy) }
        }
    }

    val connection = remember(state, policy, density, scope) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && state.swipeOffsetPx > 0f)
                    Offset(0f, dragBy(available.y)) else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y > 0f)
                    Offset(0f, dragBy(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.swipeOffsetPx == 0f || state.closing) return Velocity.Zero
                release(available.y)
                return Velocity(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (state.swipeOffsetPx == 0f || state.swipeSettling || state.closing) return Velocity.Zero
                release(available.y)
                return Velocity(0f, available.y)
            }
        }
    }
    return onSizeChanged { state.swipeExtentPx = it.height.coerceAtLeast(1).toFloat() }
        .nestedScroll(connection)
        .draggable(
            state = rememberDraggableState { dragBy(it) },
            orientation = Orientation.Vertical,
            enabled = active,
            startDragImmediately = state.swipeSettling,
            onDragStarted = { state.swipeJob?.cancel() },
            onDragStopped = { release(it) },
        )
}
