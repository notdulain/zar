package com.example.zar.core.presentation.components

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.motion.ZarSprings
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/**
 * Hi-fi floating pill navbar. Every item keeps its label visible. One retained capsule (owned by [controller], which
 * lives with the navigation session) is drawn behind the labels and moves between the measured slots with the shared
 * `navSelection` spring. The labels themselves do not own the capsule, so a screen being created again cannot reset it.
 *
 * Labels and icons are drawn in the resting colour on the pill and again, clipped to the capsule, in the inverse colour,
 * so they stay readable wherever the capsule is mid-move. Selection semantics always follow the real route, not the
 * capsule's position.
 */
@Composable
fun ZarBottomNav(
    selected: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
    controller: NavCapsuleController,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    val policy = LocalZarMotion.current
    val shape = RoundedCornerShape(ZarDimens.NavRadius)
    val selectedIndex = TopLevelDestination.entries.indexOf(selected)

    // Keep the retained capsule's target in step with the real navigation selection.
    LaunchedEffect(controller, selectedIndex) { controller.select(selectedIndex) }

    // Move from the current on-screen rectangle to the latest target. A newer selection (or a remeasured slot) cancels
    // the old flight and retargets from its position and velocity. Reduced motion and disabled animations resolve at once.
    val overshootPx = with(LocalDensity.current) { ZarMotion.NavMaxOvershoot.toPx() }
    LaunchedEffect(controller, overshootPx) { controller.maxOvershootPx = overshootPx }
    LaunchedEffect(controller, policy) {
        snapshotFlow { controller.targetRect }.filterNotNull().collectLatest { target ->
            if (policy.allowsSpatialMotion) {
                controller.moveTo(target, policy.spring(ZarSprings.NavSelection, Rect.VisibilityThreshold))
            } else {
                controller.snapTo(target)
            }
        }
    }

    // Measured tab slots in this navbar's own coordinate space. Position is physical, so RTL mirrors for free.
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    val slots = remember { arrayOfNulls<Rect>(TopLevelDestination.entries.size) }
    var interior by remember { mutableStateOf(Rect.Zero) }
    fun reportSlots() {
        val origin = containerOrigin
        if (slots.any { it == null }) return
        controller.onSlotsMeasured(
            slots.map { rect ->
                val r = rect!!
                Rect(r.left - origin.x, r.top - origin.y, r.right - origin.x, r.bottom - origin.y)
            },
            interior.takeIf { it != Rect.Zero },
        )
    }
    val borderPx = with(LocalDensity.current) { 1.dp.toPx() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(ZarDimens.NavHeight)
            .onGloballyPositioned {
                containerOrigin = it.positionInRoot()
                interior = Rect(borderPx, borderPx, it.size.width - borderPx, it.size.height - borderPx)
                reportSlots()
            }
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = Color(0x0F0F1A2B),
                spotColor = Color(0x1A0F1A2B),
            ),
        shape = shape,
        color = colors.surface,
        border = BorderStroke(1.dp, if (colors.surface == Color.White) Color.White else colors.borderDefault),
        shadowElevation = 0.dp,
    ) {
        Box(
            Modifier.fillMaxWidth().height(ZarDimens.NavHeight).drawBehind {
                // Read in the draw phase, so a moving capsule invalidates drawing only, never the whole screen.
                val rect = controller.capsuleBounds() ?: return@drawBehind
                drawRoundRect(
                    color = colors.accentSelected,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    cornerRadius = CornerRadius(rect.height / 2f),
                )
            },
        ) {
            // The labels are drawn twice at identical positions: the resting colour on the pill, and above it the colour for
            // a dark capsule, clipped to wherever the capsule is right now. A glyph is therefore always light on the
            // capsule and dark on the pill, even half-way through a move or a bounce. Only the first copy is interactive
            // or visible to accessibility.
            NavRow(
                selected = selected,
                tint = colors.navInactive,
                glyphSelected = false,
                modifier = Modifier.selectableGroup(),
                onNavigate = onNavigate,
                onSlot = { index, coords ->
                    val position = coords.positionInRoot()
                    slots[index] = Rect(position.x, position.y, position.x + coords.size.width, position.y + coords.size.height)
                    reportSlots()
                },
            )
            NavRow(
                selected = selected,
                tint = colors.textOnInverse,
                glyphSelected = true,
                modifier = Modifier.clearAndSetSemantics {}.drawWithContent {
                    val rect = controller.capsuleBounds()
                    if (rect != null) {
                        clipRect(rect.left, rect.top, rect.right, rect.bottom) { this@drawWithContent.drawContent() }
                    }
                },
                onNavigate = null,
                onSlot = null,
            )
        }
    }
}

/** One copy of the four tab slots. [onNavigate] and [onSlot] are null for the decorative copy drawn over the capsule. */
@Composable
private fun NavRow(
    selected: TopLevelDestination,
    tint: Color,
    glyphSelected: Boolean,
    modifier: Modifier,
    onNavigate: ((TopLevelDestination) -> Unit)?,
    onSlot: ((Int, LayoutCoordinates) -> Unit)?,
) {
    Row(
        modifier.fillMaxWidth().height(ZarDimens.NavHeight).padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopLevelDestination.entries.forEachIndexed { index, destination ->
            NavItem(
                destination = destination,
                isSelected = destination == selected,
                tint = tint,
                glyphSelected = glyphSelected,
                onClick = onNavigate?.let { navigate -> { navigate(destination) } },
                modifier = Modifier
                    .weight(if (destination == selected) 75f else 66f)
                    .then(if (onSlot != null) Modifier.onGloballyPositioned { onSlot(index, it) } else Modifier),
            )
        }
    }
}

@Composable
private fun NavItem(
    destination: TopLevelDestination,
    isSelected: Boolean,
    tint: Color,
    glyphSelected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.label)
    val interactive = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(if (isSelected) 46.dp else 28.dp))
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag("nav-${destination.name.lowercase()}")
    } else {
        Modifier
    }
    Column(
        modifier = modifier.height(if (isSelected) 59.dp else 56.dp).then(interactive),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TopLevelIcon(destination, tint = tint, selected = glyphSelected)
        Spacer(Modifier.height(if (isSelected) 1.dp else 2.dp))
        Text(label, style = ZarType.labelTab, color = tint)
    }
}
