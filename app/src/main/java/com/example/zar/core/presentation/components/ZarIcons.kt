package com.example.zar.core.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.theme.ZarDimens

/** The original Phosphor paths exported from the Zar Figma navigation component. */
@Composable
fun TopLevelIcon(
    destination: TopLevelDestination,
    tint: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    @DrawableRes val resource = when (destination) {
        TopLevelDestination.Home -> if (selected) R.drawable.ic_ph_house_selected else R.drawable.ic_ph_house_inactive
        TopLevelDestination.Activity -> if (selected) R.drawable.ic_ph_chartline_selected else R.drawable.ic_ph_chartline_inactive
        TopLevelDestination.Plans -> if (selected) R.drawable.ic_ph_flag_selected else R.drawable.ic_ph_flag_inactive
        TopLevelDestination.More -> if (selected) R.drawable.ic_ph_gearsix_selected else R.drawable.ic_ph_gearsix_inactive
    }
    Icon(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = modifier.size(18.dp),
        tint = tint,
    )
}

@Composable
fun PlusIcon(tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_ph_plus_nav),
        contentDescription = null,
        modifier = modifier.size(26.dp),
        tint = tint,
    )
}

/** Phosphor 2.1.1 Regular (MIT); original paths, see docs/licenses/phosphor-icons.txt. */
@Composable
internal fun AddActionIcon(option: AddEntryOption, tint: Color) {
    Icon(
        painter = painterResource(if (option == AddEntryOption.QuickNote) R.drawable.ic_ph_note_pencil else R.drawable.ic_ph_calculator),
        contentDescription = null,
        modifier = Modifier.size(ZarDimens.SpeedDialIcon),
        tint = tint,
    )
}
