package com.example.zar.features.home.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.allowance.presentation.screen.allowanceHeadline
import com.example.zar.features.allowance.presentation.screen.weekRange
import com.example.zar.features.allowance.presentation.screen.AllowanceSyncNotice
import com.example.zar.features.home.presentation.viewmodel.HomeAllowanceContent

/**
 * The weekly allowance snapshot beside the next bill. Switched off it invites setting one up;
 * switched on it shows what is left (or "Overspent by") and flags anything left out of the total.
 * Tapping opens Plans, where it is edited.
 */
@Composable
internal fun WeeklyLimitCard(
    allowance: HomeAllowanceContent,
    onOpenPlans: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    val status = (allowance as? HomeAllowanceContent.Ready)?.overview?.status
    ZarCard(
        modifier.heightIn(min = 116.dp).testTag("weekly-limit-card"),
        onClick = if (allowance is HomeAllowanceContent.Error) onRetry else onOpenPlans,
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(stringResource(R.string.home_weekly_limit).uppercase(), style = ZarType.monoLabel, color = colors.textTertiary)
        Spacer(Modifier.height(10.dp))
        when {
            allowance is HomeAllowanceContent.Error -> Text(
                stringResource(R.string.home_commitments_error),
                style = ZarType.bodyS,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            allowance !is HomeAllowanceContent.Ready ->
                Text(stringResource(R.string.home_loading_short), style = ZarType.bodyM, color = colors.textSecondary)
            status == null -> Text(
                stringResource(R.string.home_weekly_limit_empty),
                style = ZarType.bodyM,
                color = colors.textSecondary,
                modifier = Modifier.testTag("weekly-limit-value"),
            )
            else -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (status.isOverspent) Box(Modifier.size(6.dp).background(colors.attention, CircleShape))
                    Text(
                        allowanceHeadline(status),
                        style = ZarType.bodyM,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f).testTag("weekly-limit-value"),
                    )
                }
                Spacer(Modifier.height(4.dp))
                // A gap in the data is shown beside the total, never hidden behind a tap.
                Text(
                    if (status.hasCoverageWarning) stringResource(R.string.home_allowance_check) else weekRange(status.week),
                    style = ZarType.bodyCaption,
                    color = if (status.hasCoverageWarning) colors.textPrimary else colors.textSecondary,
                    fontWeight = if (status.hasCoverageWarning) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.testTag("weekly-limit-detail"),
                )
            }
        }
        (allowance as? HomeAllowanceContent.Ready)?.overview?.let { AllowanceSyncNotice(it.syncState) }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(
                when {
                    allowance is HomeAllowanceContent.Error -> R.string.home_retry
                    status != null -> R.string.home_view_allowance
                    else -> R.string.home_choose_amount
                },
            ),
            style = ZarType.labelAction,
            color = colors.textSelected,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
