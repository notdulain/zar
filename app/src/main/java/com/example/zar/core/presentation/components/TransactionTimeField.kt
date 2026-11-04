package com.example.zar.core.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.example.zar.core.presentation.components.ZarAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Optional recorded local time. Opening or cancelling the picker never invents a value. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTimeField(time: LocalTime?, modifier: Modifier = Modifier, tag: String = "entry-time", onSelect: (LocalTime?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = ZarTheme.colors
    ZarFormRow(stringResource(R.string.entry_field_time), modifier.fillMaxWidth().clickable(role = Role.Button) { open = true }.testTag(tag)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(time?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) ?: stringResource(R.string.entry_time_not_set),
                style = ZarType.bodyM, color = colors.textPrimary, modifier = Modifier.weight(1f))
            ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = colors.iconMuted)
        }
    }
    if (open) {
        val initial = time ?: LocalTime.now(ZarTime.ZONE)
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
        ZarAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.entry_field_time)) },
            text = { TimeInput(state = state, modifier = Modifier.testTag("$tag-picker")) },
            confirmButton = { TextButton(onClick = { onSelect(LocalTime.of(state.hour, state.minute)); open = false }, modifier = Modifier.testTag("$tag-confirm")) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = {
                Row {
                    if (time != null) TextButton(onClick = { onSelect(null); open = false }, modifier = Modifier.testTag("$tag-clear")) { Text(stringResource(R.string.activity_clear)) }
                    TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }
}
