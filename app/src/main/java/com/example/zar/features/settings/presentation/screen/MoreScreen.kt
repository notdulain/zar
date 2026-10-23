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

/** Z86 — More. One card per feature that exists today; later features slot in before the reminder and Settings. */
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
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.More,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
        // The dusk hero runs under the status bar, so the system glyphs must be white.
        heroUnderStatusBar = true,
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                moreRows(
                    onOpenAccounts, onOpenIncome, onOpenFreelance, onOpenCrypto,
                    onOpenCapture, onOpenBills, onOpenReminder, onOpenSettings,
                ).forEach { row -> MoreOption(row) }
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
            stringResource(R.string.more_hero_subtitle),
            style = ZarType.bodyS,
            color = Color.White,
            modifier = Modifier.padding(start = 22.dp, top = 108.dp),
        )
    }
}

/** One More card. A later story adds its row to [moreRows] and nothing else. */
internal data class MoreRow(
    @StringRes val title: Int,
    @StringRes val detail: Int,
    @DrawableRes val icon: Int,
    val tag: String,
    val onClick: () -> Unit,
)

internal fun moreRows(
    onOpenAccounts: () -> Unit,
    onOpenIncome: () -> Unit,
    onOpenFreelance: () -> Unit,
    onOpenCrypto: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenSettings: () -> Unit,
): List<MoreRow> = listOf(
    MoreRow(R.string.more_accounts, R.string.more_accounts_detail, R.drawable.ic_ph_wallet, "open-accounts", onOpenAccounts),
    MoreRow(R.string.income_open, R.string.more_income_detail, R.drawable.ic_ph_receipt, "open-income", onOpenIncome),
    MoreRow(R.string.freelance_open, R.string.more_freelance_detail, R.drawable.ic_ph_briefcase, "open-freelance", onOpenFreelance),
    MoreRow(R.string.crypto_open, R.string.more_crypto_detail, R.drawable.ic_ph_currency_btc, "open-crypto", onOpenCrypto),
    MoreRow(R.string.more_capture, R.string.more_capture_detail, R.drawable.ic_ph_chat_text, "open-capture", onOpenCapture),
    MoreRow(R.string.bills_open, R.string.more_bills_detail, R.drawable.ic_ph_tray, "open-bills", onOpenBills),
    MoreRow(R.string.more_reminder, R.string.more_reminder_detail, R.drawable.ic_ph_lightning, "open-reminder", onOpenReminder),
    MoreRow(R.string.settings_title, R.string.more_settings_detail, R.drawable.ic_ph_gearsix_inactive, "open-settings", onOpenSettings),
)

@Composable
private fun MoreOption(row: MoreRow) {
    val colors = ZarTheme.colors
    ZarCard(
        modifier = Modifier.fillMaxWidth().testTag(row.tag),
        onClick = row.onClick,
        contentPadding = PaddingValues(start = 16.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZarIconChip(row.icon, size = 40.dp, iconSize = 19.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(row.title), style = ZarType.labelButton, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(row.detail), style = ZarType.bodyCaption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ZarIcon(R.drawable.ic_ph_caret_right, size = 16.dp, tint = colors.iconMuted)
        }
    }
}
