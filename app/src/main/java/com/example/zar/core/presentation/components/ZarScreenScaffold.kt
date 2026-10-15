package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.theme.ZarDimens

/**
 * Layout shared by the four main screens.
 *
 * - [TopLevelDestination.Home]: no navbar, Add button centred at the bottom.
 * - Everything else: pill navbar at the bottom and the Add button on the right, sitting just
 *   above the navbar within thumb reach.
 *
 * [content] receives padding that already clears the navbar and the Add button, so lists can
 * scroll fully into view.
 */
@Composable
fun ZarScreenScaffold(
    destination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val isHome = destination == TopLevelDestination.Home
    val addDescription = stringResource(R.string.add_entry)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!isHome) {
                ZarBottomNav(
                    selected = destination,
                    onNavigate = onNavigate,
                    modifier = Modifier.padding(horizontal = ZarDimens.ScreenGutter, vertical = ZarDimens.SpaceSm),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                modifier = Modifier
                    .size(ZarDimens.AddButtonSize)
                    .testTag("add-entry")
                    .semantics { contentDescription = addDescription },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            ) {
                PlusIcon(tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
        },
        floatingActionButtonPosition = if (isHome) FabPosition.Center else FabPosition.End,
    ) { inner ->
        // Reserve the space the floating Add button covers so content can scroll above it.
        content(
            PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + ZarDimens.AddButtonSize + ZarDimens.SpaceXl,
            ),
        )
    }

    if (showAddSheet) {
        AddEntrySheet(
            onDismiss = { showAddSheet = false },
            onOption = { option ->
                showAddSheet = false
                onAddOption(option)
            },
        )
    }
}
