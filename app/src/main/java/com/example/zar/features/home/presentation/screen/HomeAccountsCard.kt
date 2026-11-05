package com.example.zar.features.home.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.presentation.viewmodel.AccountsUiState

/** Current native-currency balances, independent of the month selected in Home's income chart. */
@Composable
internal fun HomeAccountsCard(state: AccountsUiState, onOpenAccounts: () -> Unit, onOpenAccount: (String) -> Unit) {
    val colors = ZarTheme.colors
    ZarCard(Modifier.fillMaxWidth().testTag("home-accounts"), contentPadding = PaddingValues(horizontal = 19.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.accounts_title), style = ZarType.label, color = colors.textTertiary,
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenAccounts)
                .padding(vertical = 12.dp).testTag("home-open-accounts"),
        )
        val overview = state.overview
        when {
            state.isLoading -> Text(stringResource(R.string.home_loading_short), style = ZarType.bodyS,
                color = colors.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
            state.loadFailed || overview == null -> Text(stringResource(R.string.accounts_load_failed), style = ZarType.bodyS,
                color = colors.textSecondary, modifier = Modifier.clickable(onClick = onOpenAccounts).padding(bottom = 12.dp))
            overview.balances.isEmpty() -> Text(stringResource(R.string.accounts_empty), style = ZarType.bodyS,
                color = colors.textSecondary, modifier = Modifier.clickable(onClick = onOpenAccounts).padding(bottom = 12.dp))
            else -> overview.balances.forEachIndexed { index, balance ->
                if (index > 0) ZarDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { onOpenAccount(balance.account.id.value) }
                        .padding(vertical = 12.dp).testTag("home-account-${balance.account.id.value}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(balance.account.name, style = ZarType.bodyM, color = colors.textPrimary,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val amount = balance.current
                    Text(
                        when {
                            amount == null -> stringResource(R.string.accounts_value_unknown)
                            balance.isApproximate -> "≈ ${amount.formatWithCode()}"
                            else -> amount.formatWithCode()
                        },
                        style = ZarType.bodyM,
                        color = if (amount == null) colors.textTertiary else colors.textPrimary,
                        modifier = Modifier.testTag("home-account-balance-${balance.account.id.value}"),
                    )
                }
            }
        }
    }
}
