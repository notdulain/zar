package com.example.zar.features.catchup.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.features.bills.presentation.screen.SubscriptionSuggestionsSection
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.catchup.presentation.viewmodel.NeedsReviewUiState
import com.example.zar.features.catchup.presentation.viewmodel.NeedsReviewViewModel

/**
 * Needs review (Figma Z73, Z84): every saved entry that still needs a look, with the weekly catch-up card
 * under it. Tapping an entry opens its detail, where it can be corrected or explained. Nothing is changed here.
 */
@Composable
fun NeedsReviewScreen(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onStartCatchUp: () -> Unit,
    viewModel: NeedsReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Possible subscriptions wait here too, so they're decided during the weekly review (L06).
    NeedsReviewBody(state, onBack, onOpenEntry, onStartCatchUp, extraContent = { SubscriptionSuggestionsSection() })
}

@Composable
internal fun NeedsReviewBody(
    state: NeedsReviewUiState,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onStartCatchUp: () -> Unit,
    extraContent: @Composable () -> Unit = {},
) {
    val colors = ZarTheme.colors
    val count = state.items.size
    CatchUpScaffold(
        title = stringResource(R.string.review_title),
        subtitle = when {
            state.isLoading || state.loadFailed -> null
            count == 0 -> stringResource(R.string.review_subtitle_none)
            else -> pluralStringResource(R.plurals.review_subtitle, count, count)
        },
        onBack = onBack,
        backLabel = stringResource(R.string.action_back),
        modifier = Modifier.testTag("review-screen"),
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 72.dp), Alignment.Center) {
                CircularProgressIndicator(color = colors.accentSelected)
            }
            state.loadFailed -> Text(
                stringResource(R.string.review_load_failed),
                style = ZarType.bodyS,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp).testTag("review-failed"),
            )
            else -> {
                if (count == 0) AllClearCard() else ItemsCard(state, onOpenEntry)
                extraContent()
                WeeklyCatchUpCard(state, onStartCatchUp)
            }
        }
    }
}

@Composable
private fun AllClearCard() {
    val colors = ZarTheme.colors
    ZarCard(Modifier.fillMaxWidth().testTag("review-clear"), contentPadding = PaddingValues(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ZarIconChip(R.drawable.ic_catchup_check_circle)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.review_all_clear_title), style = ZarType.bodyM, color = colors.textPrimary)
                Text(stringResource(R.string.review_all_clear_body), style = ZarType.bodyS, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ItemsCard(state: NeedsReviewUiState, onOpenEntry: (String) -> Unit) {
    ZarCard(Modifier.fillMaxWidth().testTag("review-list"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
        state.items.forEachIndexed { index, item ->
            UnresolvedRow(item, onOpen = { onOpenEntry(item.entryId.value) })
            if (index < state.items.lastIndex) ZarDivider()
        }
    }
}

@Composable
private fun WeeklyCatchUpCard(state: NeedsReviewUiState, onStart: () -> Unit) {
    val colors = ZarTheme.colors
    val days = state.daysSinceCatchUp
    val count = state.items.size
    CatchUpCard(Modifier.testTag("review-catch-up")) {
        ZarMonoLabel(stringResource(R.string.review_catch_up_label))
        Text(
            when (days) {
                null -> stringResource(R.string.review_catch_up_never)
                0 -> stringResource(R.string.review_catch_up_last_today)
                1 -> stringResource(R.string.review_catch_up_last_yesterday)
                else -> stringResource(R.string.review_catch_up_last_days, days)
            },
            style = ZarType.bodyM,
            color = colors.textPrimary,
        )
        Text(
            if (count == 0) stringResource(R.string.review_catch_up_none_open) else pluralStringResource(R.plurals.review_catch_up_open, count, count),
            style = ZarType.bodyS,
            color = colors.textSecondary,
        )
        ZarButton(
            text = stringResource(if (count == 0) R.string.review_catch_up_anyway else R.string.review_catch_up_start),
            onClick = onStart,
            style = if (count == 0) ZarButtonStyle.Soft else ZarButtonStyle.Primary,
            modifier = Modifier.fillMaxWidth().testTag("review-start"),
        )
    }
}
