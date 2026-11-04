package com.example.zar.core.presentation.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.ZarSprings
import com.example.zar.core.presentation.theme.ZarPrimitives
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The navy scrim Figma puts behind a bottom sheet (#05070B at 55%). */
val SheetScrim = Color(0x8C05070B)

/** The light 36x4 grabber, 10dp below the sheet top and 14dp above the title. */
@Composable
fun SheetGrabber() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 14.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(ZarPrimitives.gray300),
    )
}

/**
 * A Material modal bottom sheet whose Back (system button, predictive gesture) and in-sheet close or Cancel leave toward the
 * physical right, like every other app-owned surface, with no fade or zoom. [content] receives `dismiss`: call it from the
 * sheet's own close and Cancel buttons; it plays the departure and then calls [onDismissRequest]. Dragging the sheet down
 * or tapping the scrim is not Back and keeps Material's own downward dismissal. With motion reduced or off, `dismiss`
 * and Back resolve immediately. [canDismiss] false (for example while saving) makes Back and `dismiss` do nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZarModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    canDismiss: Boolean = true,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape: Shape = ZarShapes.sheet,
    containerColor: Color = ZarTheme.colors.surface,
    dragHandle: @Composable (() -> Unit)? = { SheetGrabber() },
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val policy = LocalZarMotion.current
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(0f) }
    val dismissing = remember { booleanArrayOf(false) }
    val currentDismiss = rememberUpdatedState(onDismissRequest)
    val allowed = rememberUpdatedState(canDismiss)

    fun dismiss() {
        if (!allowed.value || dismissing[0]) return
        dismissing[0] = true
        if (!policy.allowsSpatialMotion) {
            currentDismiss.value()
            return
        }
        scope.launch {
            slide.animateTo(1f, spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness))
            currentDismiss.value()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.graphicsLayer { translationX = slide.value * size.width },
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        scrimColor = SheetScrim.copy(alpha = SheetScrim.alpha * (1f - slide.value.coerceIn(0f, 1f))),
        dragHandle = dragHandle,
        // Back is handled below so the sheet can leave rightward instead of sinking.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        PredictiveBackHandler(enabled = !dismissing[0]) { events ->
            try {
                events.collect { event -> if (policy.allowsSpatialMotion && allowed.value) slide.snapTo(PREVIEW_FACTOR * event.progress) }
                dismiss()
            } catch (cancelled: CancellationException) {
                scope.launch { slide.animateTo(0f, spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness)) }
                throw cancelled
            }
        }
        content(::dismiss)
    }
}
