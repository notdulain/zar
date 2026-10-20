package com.example.zar.features.accounts.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.presentation.components.ZarAddPill
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarDataCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarSectionHeader
import com.example.zar.core.presentation.components.ZarTitleBlock
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.domain.model.AccountsOverview
import com.example.zar.features.accounts.presentation.viewmodel.AccountsViewModel
import com.example.zar.core.domain.model.AccountType
import com.example.zar.features.transactions.presentation.income.IncomeFormat

/** Z26 — Accounts. */
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenAccount: (String) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ZarTheme.colors
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            ZarBackButton(onBack, stringResource(R.string.accounts_back), Modifier.padding(start = 10.dp, top = 4.dp))
            val overview = uiState.overview
            ZarTitleBlock(
                title = stringResource(R.string.accounts_title),
                subtitle = overview?.let { subtitle(it) },
                modifier = Modifier.padding(top = 6.dp),
            )
            when {
                uiState.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
                uiState.loadFailed || overview == null -> Message(stringResource(R.string.accounts_load_failed))
                overview.isEmpty -> Message(stringResource(R.string.accounts_empty))
                else -> AccountSections(overview, onOpenAccount)
            }
            // Room so the last card clears the floating Add account pill.
            Spacer(Modifier.height(120.dp))
        }
        ZarAddPill(
            text = stringResource(R.string.accounts_add),
            onClick = onAddAccount,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        )
    }
}

@Composable
private fun subtitle(overview: AccountsOverview): String {
    val total = overview.total ?: return stringResource(R.string.accounts_subtitle_none)
    return stringResource(
        R.string.accounts_subtitle_total,
        IncomeFormat.withCode(total),
        pluralStringResource(R.plurals.accounts_count, overview.countedAccounts, overview.countedAccounts),
    )
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
private fun AccountSections(overview: AccountsOverview, onOpenAccount: (String) -> Unit) {
    val money = overview.balances.filter { it.account.type != AccountType.CRYPTO }
    val crypto = overview.balances.filter { it.account.type == AccountType.CRYPTO }
    Column(Modifier.padding(top = 18.dp)) {
        if (money.isNotEmpty()) Section(stringResource(R.string.accounts_section_money), money, onOpenAccount)
        if (crypto.isNotEmpty()) Section(stringResource(R.string.accounts_section_crypto), crypto, onOpenAccount, Modifier.padding(top = 18.dp))
        if (overview.archived.isNotEmpty()) {
            Section(stringResource(R.string.accounts_section_archived), overview.archived, onOpenAccount, Modifier.padding(top = 18.dp))
        }
        Text(
            buildString {
                append(stringResource(R.string.accounts_footnote))
                if (overview.otherCurrencyAccounts > 0) append(' ').append(stringResource(R.string.accounts_footnote_other_currency))
            },
            style = ZarType.bodyS,
            color = ZarTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp),
        )
    }
}

@Composable
private fun Section(title: String, balances: List<AccountBalance>, onOpenAccount: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        ZarSectionHeader(title)
        ZarDataCard(Modifier.padding(horizontal = 10.dp).padding(top = 10.dp).fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                balances.forEachIndexed { index, balance ->
                    if (index > 0) ZarDivider()
                    AccountRow(balance, onClick = { onOpenAccount(balance.account.id.value) })
                }
            }
        }
    }
}

@Composable
private fun AccountRow(balance: AccountBalance, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val account = balance.account
    val type = stringResource(account.type.labelRes())
    val meta = when {
        balance.current == null -> stringResource(R.string.accounts_meta_unknown, "$type · ${account.currency.code}")
        balance.mismatchedCount > 0 -> stringResource(R.string.accounts_meta_mismatch, "$type · ${account.currency.code}")
        balance.isApproximate -> stringResource(R.string.accounts_meta_approximate, "$type · ${account.currency.code}")
        else -> "$type · ${account.currency.code}"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZarIconChip(account.type.iconRes())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(account.name, style = ZarType.labelButton, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = ZarType.bodyCaption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val current = balance.current
        Text(
            text = when {
                current == null -> stringResource(R.string.accounts_value_unknown)
                balance.isApproximate -> "≈ ${IncomeFormat.withCode(current)}"
                else -> IncomeFormat.withCode(current)
            },
            style = ZarType.bodyM,
            color = if (current == null) colors.textTertiary else colors.textPrimary,
            maxLines = 1,
        )
    }
}
