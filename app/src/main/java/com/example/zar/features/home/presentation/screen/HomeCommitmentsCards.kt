package com.example.zar.features.home.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.home.presentation.viewmodel.HomeCommitmentsContent
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DueFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/** One total per currency, never converted: "LKR 45,000 + USD 300". */
private fun List<Money>.display(): String = joinToString(" + ") { it.formatWithCode() }

/**
 * Compact "Clients owe you" row. Only expected client money that has not been received is
 * counted, so it never feeds Received. The caption under the total says so and shows any
 * overdue amount. Tapping opens the freelance projects.
 */
@Composable
internal fun ClientsOweRow(commitments: HomeCommitmentsContent, onClick: () -> Unit, onRetry: () -> Unit) {
    val colors = ZarTheme.colors
    val outstanding = (commitments as? HomeCommitmentsContent.Ready)?.commitments?.outstanding
    ZarCard(
        Modifier.fillMaxWidth().testTag("clients-owe-row"),
        onClick = if (commitments is HomeCommitmentsContent.Error) onRetry else onClick,
        contentPadding = PaddingValues(horizontal = 19.dp, vertical = 14.dp),
    ) {
        Text(stringResource(R.string.freelance_owed_label), style = ZarType.label, color = colors.textTertiary)
        Spacer(Modifier.height(8.dp))
        when {
            commitments is HomeCommitmentsContent.Error -> {
                Text(stringResource(R.string.home_commitments_error), style = ZarType.bodyM, color = colors.textPrimary)
                Text(
                    stringResource(R.string.home_retry),
                    style = ZarType.labelAction,
                    color = colors.textSelected,
                    modifier = Modifier.align(Alignment.End).testTag("commitments-retry"),
                )
            }
            outstanding == null ->
                Text(stringResource(R.string.home_loading_short), style = ZarType.bodyM, color = colors.textSecondary)
            outstanding.isEmpty -> {
                Text(
                    stringResource(R.string.freelance_owed_none),
                    style = ZarType.bodyM,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("clients-owe-amount"),
                )
                Text(
                    stringResource(R.string.home_view_freelance),
                    style = ZarType.labelAction,
                    color = colors.textSelected,
                    modifier = Modifier.align(Alignment.End),
                )
            }
            else -> {
                Text(
                    outstanding.owed.display(),
                    style = ZarType.bodyL,
                    color = colors.textPrimary,
                    modifier = Modifier.testTag("clients-owe-amount"),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (outstanding.overdue.isNotEmpty()) {
                        Box(Modifier.size(6.dp).background(colors.attention, CircleShape))
                    }
                    Text(
                        if (outstanding.overdue.isNotEmpty()) {
                            stringResource(R.string.freelance_overdue, outstanding.overdue.display())
                        } else {
                            pluralStringResource(
                                R.plurals.freelance_owed_caption,
                                outstanding.projectsWithBalance,
                                outstanding.projectsWithBalance,
                            )
                        },
                        style = ZarType.bodyCaption,
                        color = colors.textSecondary,
                        modifier = Modifier.testTag("clients-owe-caption"),
                    )
                }
                Text(
                    stringResource(R.string.home_view_freelance),
                    style = ZarType.labelAction,
                    color = colors.textSelected,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

/**
 * The next bill: name, due date and expected amount. The amount is what the bill expects, not a
 * claim that it is unpaid; a payment is linked from the bill's own screen. With no bill it
 * links to the bills list instead.
 */
@Composable
internal fun NextBillCard(
    commitments: HomeCommitmentsContent,
    onOpenBill: (String) -> Unit,
    onOpenBills: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    val nextBill = (commitments as? HomeCommitmentsContent.Ready)?.commitments?.nextBill
    val onClick: () -> Unit = when {
        commitments is HomeCommitmentsContent.Error -> onRetry
        nextBill != null -> {
            { onOpenBill(nextBill.billId.value) }
        }
        else -> onOpenBills
    }
    ZarCard(
        modifier.heightIn(min = 116.dp).testTag("next-bill-card"),
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(stringResource(R.string.home_next_bill_label).uppercase(), style = ZarType.label, color = colors.textTertiary)
        Spacer(Modifier.height(10.dp))
        when {
            commitments is HomeCommitmentsContent.Error ->
                Text(
                    stringResource(R.string.home_commitments_error),
                    style = ZarType.bodyS,
                    color = colors.textPrimary,
                )
            commitments !is HomeCommitmentsContent.Ready ->
                Text(stringResource(R.string.home_loading_short), style = ZarType.bodyM, color = colors.textSecondary)
            nextBill == null ->
                Text(
                    stringResource(R.string.home_bills_empty),
                    style = ZarType.bodyM,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("next-bill-name"),
                )
            else -> {
                Text(
                    nextBill.name,
                    style = ZarType.bodyM,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("next-bill-name"),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.home_next_bill_due,
                        nextBill.dueDate.format(DueFormat),
                        nextBill.amount.formatWithCode(),
                    ),
                    style = ZarType.bodyCaption,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("next-bill-detail"),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                when {
                    commitments is HomeCommitmentsContent.Error -> R.string.home_retry
                    nextBill != null -> R.string.home_view_bill
                    else -> R.string.home_add_bill
                },
            ),
            style = ZarType.labelAction,
            color = colors.textSelected,
        )
    }
}
