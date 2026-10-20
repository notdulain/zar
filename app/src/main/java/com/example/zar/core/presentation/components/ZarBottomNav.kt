package com.example.zar.core.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType

/** Hi-fi floating pill navbar. Every item keeps its label visible. */
@Composable
fun ZarBottomNav(
    selected: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    val shape = RoundedCornerShape(ZarDimens.NavRadius)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(ZarDimens.NavHeight)
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
        Row(
            modifier = Modifier.selectableGroup().padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                NavItem(
                    destination = destination,
                    isSelected = destination == selected,
                    onClick = { onNavigate(destination) },
                    modifier = Modifier.weight(if (destination == selected) 75f else 66f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: TopLevelDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.label)
    val colors = ZarTheme.colors
    val inactive = colors.navInactive
    Column(
        modifier = modifier
            .height(if (isSelected) 59.dp else 56.dp)
            .clip(RoundedCornerShape(if (isSelected) 46.dp else 28.dp))
            .background(if (isSelected) colors.accentSelected else Color.Transparent)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag("nav-${destination.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TopLevelIcon(destination, tint = if (isSelected) colors.textOnInverse else inactive, selected = isSelected)
        Spacer(Modifier.height(if (isSelected) 1.dp else 2.dp))
        Text(
            label,
            style = ZarType.labelTab,
            color = if (isSelected) colors.textOnInverse else inactive,
        )
    }
}
