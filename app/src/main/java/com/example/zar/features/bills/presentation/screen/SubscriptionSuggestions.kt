package com.example.zar.features.bills.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.bills.domain.RecurringSuggestion
import com.example.zar.features.bills.presentation.viewmodel.SubscriptionSuggestionsViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat

/**
 * Possible subscriptions waiting for a decision, in the Bills list and the weekly review. Rows follow
 * Figma's Z22 "Needs a look" card. Shows nothing when there are none.
 *
 * [edge] is the card's distance from the screen edge; the weekly review already pads its content.
 */
@Composable
fun SubscriptionSuggestionsSection(
    modifier: Modifier = Modifier,
    edge: Dp = 10.dp,
    trailingGap: Dp = 0.dp,
    viewModel: SubscriptionSuggestionsViewModel = hiltViewModel(),
) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    if (suggestions.isEmpty()) return
    val colors = ZarTheme.colors
    Column(modifier.fillMaxWidth().testTag("subscription-suggestions")) {
        ZarEyebrow(stringResource(R.string.subs_label), Modifier.padding(horizontal = edge + 12.dp))
        Text(
            stringResource(R.string.subs_intro),
            style = ZarType.bodyS.copy(lineHeight = 19.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = edge + 12.dp).padding(top = 6.dp),
        )
        if (failed) {
            Text(
                stringResource(R.string.subs_failed),
                style = ZarType.bodyCaption,
                color = colors.danger,
                modifier = Modifier.padding(horizontal = edge + 12.dp).padding(top = 6.dp),
            )
        }
        ZarCard(
            Modifier.padding(horizontal = edge).padding(top = 10.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) ZarDivider()
                SuggestionRow(suggestion, onConfirm = { viewModel.confirm(suggestion) }, onDismiss = { viewModel.dismiss(suggestion) })
            }
        }
        if (trailingGap > 0.dp) Spacer(Modifier.height(trailingGap))
    }
}

@Composable
private fun SuggestionRow(suggestion: RecurringSuggestion, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = ZarTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ZarIconChip(BillFormat.icon(suggestion.merchant), size = 36.dp, iconSize = 18.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(suggestion.merchant, style = ZarType.labelButton.copy(lineHeight = 19.sp), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(colors.attention)
                    Text(
                        stringResource(
                            R.string.subs_seen,
                            IncomeFormat.withCode(suggestion.amount),
                            BillFormat.dayMonth(suggestion.previousDate),
                            BillFormat.dayMonth(suggestion.lastDate),
                        ),
                        style = ZarType.bodyCaption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // Actions line up under the text, past the 36dp chip and its 12dp gap.
        Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                stringResource(R.string.subs_confirm) + " →",
                style = ZarType.labelAction,
                color = colors.textSelected,
                modifier = Modifier.clickable(role = Role.Button, onClick = onConfirm),
            )
            Text(
                stringResource(R.string.subs_dismiss),
                style = ZarType.labelAction,
                color = colors.textSecondary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onDismiss),
            )
        }
    }
}
