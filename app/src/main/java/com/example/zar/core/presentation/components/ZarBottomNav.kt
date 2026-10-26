package com.example.zar.core.presentation.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.ZarSprings
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import kotlin.math.abs
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest

/**
 * Hi-fi floating pill navbar. Every item keeps its label visible. One retained capsule (owned by [controller], which
 * lives with the navigation session) is drawn behind the labels and moves between the measured slots with the shared
 * `navSelection` spring. The labels themselves do not own the capsule, so a screen being created again cannot reset it.
 *
 * A label switches to its inverse colour only once the capsule actually covers it, so it is never white on white
 * while the capsule is between tabs. Selection semantics always follow the real route, not the capsule's position.
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

    val capsule = remember(controller) { Animatable(controller.presentationIndex) }
    // Move from the current presentation to the latest target. A newer selection cancels the old flight and retargets.
    LaunchedEffect(controller, policy) {
        snapshotFlow { controller.targetIndex }.collectLatest { target ->
            capsule.animateTo(target.toFloat(), policy.spring(ZarSprings.NavSelection))
        }
    }
    // Publish the moving value for the draw phase; resolve at once when motion is reduced or system animations are off.
    LaunchedEffect(controller, policy) {
        if (!policy.allowsSpatialMotion) capsule.snapTo(controller.targetIndex.toFloat())
        snapshotFlow { capsule.value }.collect { controller.setPresentation(it) }
    }

    // Measured tab slots in this navbar's own coordinate space. Position is physical, so RTL mirrors for free.
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    val slots = remember { arrayOfNulls<Rect>(TopLevelDestination.entries.size) }
    fun reportSlots() {
        val origin = containerOrigin
        if (slots.any { it == null }) return
        controller.onSlotsMeasured(
            slots.map { rect ->
                val r = rect!!
                Rect(r.left - origin.x, r.top - origin.y, r.right - origin.x, r.bottom - origin.y)
            },
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(ZarDimens.NavHeight)
            .onGloballyPositioned {
                containerOrigin = it.positionInRoot()
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
            Row(
                Modifier.selectableGroup().fillMaxWidth().height(ZarDimens.NavHeight).padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEachIndexed { index, destination ->
                    NavItem(
                        destination = destination,
                        index = index,
                        isSelected = destination == selected,
                        controller = controller,
                        onClick = { onNavigate(destination) },
                        modifier = Modifier
                            .weight(if (destination == selected) 75f else 66f)
                            .onGloballyPositioned { coords ->
                                val position = coords.positionInRoot()
                                slots[index] = Rect(
                                    position.x,
                                    position.y,
                                    position.x + coords.size.width,
                                    position.y + coords.size.height,
                                )
                                reportSlots()
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: TopLevelDestination,
    index: Int,
    isSelected: Boolean,
    controller: NavCapsuleController,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.label)
    val colors = ZarTheme.colors
    val inactive = colors.navInactive
    // The capsule covers this label when its centre is within half a slot. Then the inverse colour is always on the
    // dark selected pill; otherwise the inactive colour is on the light surface. Never white on white.
    val onCapsule by remember(controller, index) {
        derivedStateOf { abs(controller.presentationIndex - index) < 0.5f }
    }
    val tint = if (onCapsule) colors.textOnInverse else inactive
    Column(
        modifier = modifier
            .height(if (isSelected) 59.dp else 56.dp)
            .clip(RoundedCornerShape(if (isSelected) 46.dp else 28.dp))
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag("nav-${destination.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TopLevelIcon(destination, tint = tint, selected = onCapsule)
        Spacer(Modifier.height(if (isSelected) 1.dp else 2.dp))
        Text(label, style = ZarType.labelTab, color = tint)
    }
}
