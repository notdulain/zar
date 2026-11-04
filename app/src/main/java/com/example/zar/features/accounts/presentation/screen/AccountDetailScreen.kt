package com.example.zar.features.accounts.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.components.ZarSectionHeader
import com.example.zar.core.presentation.components.ZarTitleBlock
import com.example.zar.core.presentation.components.HeroScrollOffset
import com.example.zar.core.presentation.components.heroDisplacement
import com.example.zar.core.presentation.components.heroParallax
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.domain.AccountRules
import com.example.zar.features.accounts.domain.model.AccountDetail
import com.example.zar.features.accounts.presentation.viewmodel.AccountDetailViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat

/** Z27 — Account detail. */
@Composable
fun AccountDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddMissingEntry: (accountId: String, amount: String) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCheck by rememberSaveable { mutableStateOf(false) }
    val colors = ZarTheme.colors
    val detail = uiState.detail
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        val scrollState = rememberScrollState()
        val heroOffset = scrollState.heroDisplacement()
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).statusBarsPadding()) {
            ZarBackButton(onBack, stringResource(R.string.accounts_back), Modifier.padding(start = 10.dp, top = backTop))
            when {
                uiState.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
                uiState.loadFailed -> Message(stringResource(R.string.accounts_load_failed))
                detail == null -> Message(stringResource(R.string.account_not_found))
                else -> {
                    DetailContent(detail, onEdit, heroOffset)
                    if (uiState.actionFailed) {
                        Text(
                            stringResource(R.string.account_action_failed),
                            style = ZarType.bodyS,
                            color = colors.danger,
                            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp),
                        )
                    }
                }
            }
            // Room so the last card clears the action buttons.
            Spacer(Modifier.height(190.dp))
        }
        if (detail != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 9.dp, end = 9.dp, bottom = 22.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ZarButton(
                    text = stringResource(if (detail.account.type == AccountType.CASH) R.string.balance_check_button_cash else R.string.balance_check_button),
                    onClick = { showCheck = true },
                    style = ZarButtonStyle.Soft,
                    modifier = Modifier.fillMaxWidth().testTag("account-check"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ZarButton(
                        text = stringResource(if (detail.account.isArchived) R.string.account_restore else R.string.account_archive),
                        onClick = viewModel::toggleArchived,
                        style = ZarButtonStyle.Outline,
                        enabled = !uiState.isWorking,
                        modifier = Modifier.weight(1f),
                    )
                    ZarButton(
                        text = stringResource(R.string.account_edit),
                        onClick = { onEdit(detail.account.id.value) },
                        style = ZarButtonStyle.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (detail != null && showCheck) {
            CheckBalanceSheet(onDismiss = { showCheck = false }, onAddMissingEntry = onAddMissingEntry)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = ZarType.bodyS,
        color = ZarTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 72.dp),
    )
}

@Composable
private fun DetailContent(detail: AccountDetail, onEdit: (String) -> Unit, heroOffset: HeroScrollOffset) {
    val account = detail.account
    val colors = ZarTheme.colors
    // Title block and balance card are the top summary group; they drift together at the bounded text rate.
    val summaryLayer = Modifier.heroParallax(heroOffset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap)
    ZarTitleBlock(
        title = account.name,
        subtitle = "${stringResource(account.type.longLabelRes())} · ${account.currency.code}",
        modifier = summaryLayer.padding(top = 6.dp),
    )
    BalanceCard(detail, Modifier.heroParallax(heroOffset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap).padding(horizontal = 10.dp).padding(top = 18.dp))
    DetailsCard(detail, onEdit, Modifier.padding(horizontal = 10.dp).padding(top = 12.dp))
    ZarSectionHeader(stringResource(R.string.account_recent), Modifier.padding(top = 18.dp))
    if (detail.recent.isEmpty()) {
        Text(
            stringResource(R.string.account_recent_empty),
            style = ZarType.bodyS,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp),
        )
    } else {
        ZarCard(Modifier.padding(horizontal = 10.dp).padding(top = 10.dp).fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                detail.recent.forEachIndexed { index, entry ->
                    if (index > 0) ZarDivider()
                    RecentRow(entry)
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(detail: AccountDetail, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    val balance = detail.balance
    val current = balance.current
    ZarCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ZarEyebrow(stringResource(R.string.account_balance_label))
            if (current == null) {
                Text(stringResource(R.string.account_balance_unknown), style = ZarType.amountL, color = colors.textTertiary)
            } else {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.textTertiary)) { append(if (balance.isApproximate) "≈ ${current.currency.code} " else "${current.currency.code} ") }
                        append(IncomeFormat.amount(current))
                    },
                    style = ZarType.amountL,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val account = detail.account
            val (note, needsAttention) = when {
                balance.mismatchedCount > 0 -> stringResource(R.string.account_mismatch_note, balance.mismatchedCount) to true
                current == null -> stringResource(R.string.account_balance_unknown_note) to true
                account.isArchived -> stringResource(R.string.account_balance_archived_note) to true
                balance.isApproximate -> stringResource(R.string.account_balance_approximate_note) to true
                else -> stringResource(R.string.account_balance_known_note) to false
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (needsAttention) colors.attention else colors.fresh))
                Text(note, style = ZarType.bodyS, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun DetailsCard(detail: AccountDetail, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    val account = detail.account
    val balance = detail.balance
    val opening = when (val opening = account.openingBalance) {
        is OpeningBalance.Known -> stringResource(R.string.account_opening_known, IncomeFormat.withCode(opening.amount), IncomeFormat.shortDate(account.openingDate))
        is OpeningBalance.Approximate -> stringResource(R.string.account_opening_approximate, IncomeFormat.withCode(opening.amount), IncomeFormat.shortDate(account.openingDate))
        OpeningBalance.Unknown -> stringResource(R.string.account_opening_unknown)
    }
    val inTotal = when {
        account.isArchived -> stringResource(R.string.account_in_total_archived)
        account.currency != Currencies.REPORTING -> stringResource(R.string.account_in_total_other_currency, account.currency.code)
        balance.current == null -> stringResource(R.string.account_in_total_unknown)
        else -> stringResource(R.string.account_in_total_yes)
    }
    ZarCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            DetailRow(stringResource(R.string.account_row_opening), opening, onClick = { onEdit(account.id.value) })
            ZarDivider()
            DetailRow(stringResource(R.string.account_row_in_total), inTotal)
            ZarDivider()
            DetailRow(stringResource(R.string.account_row_currency), stringResource(R.string.account_currency_value, account.currency.code))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(110.dp).height(16.dp), Alignment.CenterStart) { ZarEyebrow(label) }
        Text(value, style = ZarType.bodyM, color = ZarTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        if (onClick != null) ZarIcon(R.drawable.ic_ph_caret_right, size = 14.dp, tint = ZarTheme.colors.iconMuted)
    }
}

@Composable
private fun RecentRow(entry: LedgerEntry) {
    val colors = ZarTheme.colors
    val tx = entry.current
    val title = tx.merchant ?: tx.note ?: stringResource(tx.type.labelRes())
    val sign = if (tx.type.direction == Direction.IN) "+" else "−"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZarIconChip(tx.type.iconRes())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ZarType.labelButton, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.account_entry_meta, stringResource(tx.type.labelRes()), IncomeFormat.shortDate(tx.date)),
                style = ZarType.bodyCaption,
                color = colors.textSecondary,
            )
        }
        Text("$sign ${IncomeFormat.amount(tx.amount)}", style = ZarType.bodyM, color = colors.textPrimary, maxLines = 1)
    }
}
