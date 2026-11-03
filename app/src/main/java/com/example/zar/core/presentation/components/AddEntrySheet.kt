package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.zar.R
import com.example.zar.core.presentation.theme.ZarDimens

/** "Add an entry" sheet opened from the Add button. Picking an option closes it and reports the choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntrySheet(
    onDismiss: () -> Unit,
    onOption: (AddEntryOption) -> Unit,
) {
    ZarModalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = ZarDimens.SheetRadius, topEnd = ZarDimens.SheetRadius),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) { dismiss ->
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = ZarDimens.SpaceXl)
                .padding(bottom = ZarDimens.SpaceLg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.add_sheet_title).uppercase(), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = dismiss) {
                    Text(stringResource(R.string.action_close), style = MaterialTheme.typography.labelLarge)
                }
            }
            // Voice is optional stretch work, outside the launch scope, so it isn't offered (Figma Z31).
            AddOption(R.string.add_quick_note, R.string.add_quick_note_hint, "add-quick-note") {
                onOption(AddEntryOption.QuickNote)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            AddOption(R.string.add_manual, R.string.add_manual_hint, "add-manual") { onOption(AddEntryOption.Manual) }
        }
    }
}

@Composable
private fun AddOption(title: Int, hint: Int, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(vertical = ZarDimens.SpaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceXs)) {
                Text(stringResource(title).uppercase(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
