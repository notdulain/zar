package com.example.zar.features.settings.presentation.screen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarGradients
import com.example.zar.core.presentation.theme.ZarType

/** Z05 — More. The four cards retain the destinations available in this sprint. */
@Composable
fun MoreScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenIncome: () -> Unit,
    onOpenFreelance: () -> Unit,
    onOpenCrypto: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenRules: () -> Unit = {},
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.More,
        heroUnderStatusBar = true,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .background(ZarTheme.colors.canvas)
                .verticalScroll(rememberScrollState())
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            MoreHero()
            Column(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MoreOption(R.string.more_accounts, R.string.more_accounts_detail, R.drawable.ic_ph_wallet, onOpenAccounts, "open-accounts")
                MoreOption(R.string.income_open, R.string.more_income_detail, R.drawable.ic_ph_receipt, onOpenIncome, "open-income")
                MoreOption(R.string.freelance_open, R.string.more_freelance_detail, R.drawable.ic_ph_briefcase, onOpenFreelance, "open-freelance")
                MoreOption(R.string.crypto_open, R.string.more_crypto_detail, R.drawable.ic_ph_currency_btc, onOpenCrypto, "open-crypto")
                MoreOption(R.string.more_capture, R.string.more_capture_detail, R.drawable.ic_ph_chat_text, onOpenCapture, "open-capture")
                MoreOption(R.string.bills_open, R.string.more_bills_detail, R.drawable.ic_ph_tray, onOpenBills, "open-bills")
                MoreOption(R.string.more_rules, R.string.more_rules_detail, R.drawable.ic_ph_funnel, onOpenRules, "open-rules")
                MoreOption(R.string.more_reminder, R.string.more_reminder_detail, R.drawable.ic_ph_lightning, onOpenReminder, "open-reminder")
                MoreOption(R.string.settings_title, R.string.more_settings_detail, R.drawable.ic_ph_gearsix_inactive, onOpenSettings, "open-settings")
            }
        }
    }
}

@Composable
private fun MoreHero() {
    Box(
        Modifier.fillMaxWidth().height(168.dp)
            .background(ZarGradients.dusk, RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)),
    ) {
        Text(
            stringResource(R.string.nav_more),
            style = ZarType.displayTitle,
            color = Color.White,
            modifier = Modifier.padding(start = 22.dp, top = 62.dp),
        )
        Text(
            "Your accounts and app settings",
            style = ZarType.bodyS,
            color = Color.White,
            modifier = Modifier.padding(start = 22.dp, top = 108.dp),
        )
    }
}

@Composable
private fun MoreOption(
    @StringRes title: Int,
    @StringRes detail: Int,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    tag: String,
) {
    val colors = ZarTheme.colors
    ZarCard(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        onClick = onClick,
        contentPadding = PaddingValues(start = 16.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZarIconChip(icon, size = 40.dp, iconSize = 19.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(title), style = ZarType.labelButton, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(detail), style = ZarType.bodyCaption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ZarIcon(R.drawable.ic_ph_caret_right, size = 16.dp, tint = colors.iconMuted)
        }
    }
}
