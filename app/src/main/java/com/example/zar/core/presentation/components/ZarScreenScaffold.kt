package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
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
 * The pill navbar and Add button share one floating row on every top-level screen.
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
    val addDescription = stringResource(R.string.add_entry)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = ZarDimens.ScreenGutter, vertical = ZarDimens.SpaceMd),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZarDimens.SpaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZarBottomNav(
                        selected = destination,
                        onNavigate = onNavigate,
                        modifier = Modifier.weight(1f),
                    )
                    ZarAddButton(addDescription) { showAddSheet = true }
                }
            }
        },
    ) { inner ->
        // Reserve the space the floating Add button covers so content can scroll above it.
        content(
            PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + ZarDimens.SpaceMd,
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

@Composable
private fun ZarAddButton(description: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(68.dp)
            .testTag("add-entry")
            .semantics { contentDescription = description },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        elevation = FloatingActionButtonDefaults.elevation(6.dp, 6.dp, 6.dp, 6.dp),
    ) {
        PlusIcon(tint = MaterialTheme.colorScheme.inverseOnSurface)
    }
}
