package com.example.zar.features.crypto.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.bills.presentation.screen.BillCard
import com.example.zar.features.bills.presentation.screen.BillNote
import com.example.zar.features.bills.presentation.screen.StatusBarGlyphs
import com.example.zar.features.bills.presentation.screen.BillFormat
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import com.example.zar.features.crypto.domain.model.CryptoMovementRow
import com.example.zar.features.crypto.domain.model.CryptoPortfolio
import com.example.zar.features.crypto.presentation.model.CryptoOverview
import com.example.zar.features.crypto.presentation.viewmodel.CryptoViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat

/** Z94 — one crypto movement: its LKR value, cost removed and result, the two linked entries, and Undo. */
@Composable
fun CryptoMovementDetailScreen(
    movementId: String,
    onBack: () -> Unit,
    viewModel: CryptoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val portfolio = uiState.portfolio
    val row = portfolio?.movements?.firstOrNull { it.outEntry.id.value == movementId }
    var confirming by rememberSaveable { mutableStateOf(false) }
    // Undone: both sides are gone, so there is nothing left to show here.
    LaunchedEffect(uiState.undone) { if (uiState.undone?.value == movementId) onBack() }
    val colors = ZarTheme.colors
    StatusBarGlyphs(onDarkHero = false)
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = backTop)) {
                ZarBackButton(onBack, stringResource(R.string.crypto_back))
            }
            when {
                uiState.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentSelected)
                }
                uiState.loadFailed && portfolio == null -> BillNote(stringResource(R.string.crypto_load_failed), Modifier.padding(top = 24.dp))
                row == null || portfolio == null -> BillNote(stringResource(R.string.crypto_movement_gone), Modifier.padding(top = 24.dp))
                else -> Detail(row, portfolio)
            }
            uiState.message?.let { FieldError(stringResource(it.textRes()), Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp)) }
            Spacer(Modifier.height(124.dp))
        }
        if (row != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.crypto_undo_hint), style = ZarType.bodyCaption, color = colors.textSecondary, modifier = Modifier.padding(bottom = 14.dp))
                ZarButton(
                    stringResource(R.string.crypto_undo_movement),
                    onClick = { confirming = true },
                    style = ZarButtonStyle.Bounded,
                    icon = R.drawable.ic_ph_trash,
                    contentColor = colors.textAttention,
                    modifier = Modifier.testTag("crypto-undo"),
                )
            }
        }
    }
    if (confirming && row != null) {
        UndoMovementSheet(
            row = row,
            isSaving = uiState.isSaving,
            onUndo = { viewModel.undo(row); confirming = false },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun Detail(row: CryptoMovementRow, portfolio: CryptoPortfolio) {
    val colors = ZarTheme.colors
    val out = row.outEntry.current
    val into = row.inEntry.current
    val value = CryptoOverview.lkrValue(row)
    val accounts = portfolio.wallets.associateBy { it.id }
    Column {
        ZarIconChip(R.drawable.ic_ph_arrows_left_right, Modifier.padding(start = 22.dp, top = 10.dp), size = 44.dp, iconSize = 20.dp)
        Text(
            "${movementTitle(row)} ${IncomeFormat.amount(out.amount)}",
            style = ZarType.bodyL.copy(lineHeight = 22.sp),
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp),
        )
        Text(
            "→ ${into.amount.withCode()}",
            style = ZarType.amountM.copy(lineHeight = 34.sp),
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 22.dp, top = 3.dp).testTag("crypto-detail-amount"),
        )
        Text(
            "${row.fromName} → ${row.toName} · ${BillFormat.dayMonth(row.date)}",
            style = ZarType.bodyCaption.copy(lineHeight = 16.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp),
        )

        BillCard(Modifier.padding(top = 19.dp).testTag("crypto-detail")) {
            CryptoDetailRow(stringResource(R.string.crypto_detail_value), value?.withCode() ?: stringResource(R.string.crypto_no_value), if (value == null) colors.danger else null)
            row.result?.let { result ->
                CryptoOverview.costRemoved(row)?.let {
                    ZarDivider()
                    CryptoDetailRow(stringResource(R.string.crypto_detail_cost_removed), it.withCode())
                }
                ZarDivider()
                val amount = result.amount
                CryptoDetailRow(
                    stringResource(if (amount?.isNegative == true) R.string.crypto_detail_realized_loss else R.string.crypto_detail_realized_profit),
                    when {
                        amount == null -> stringResource(R.string.crypto_result_unknown)
                        amount.isNegative -> FiatMoney(-amount.minorUnits, amount.currency).withCode()
                        else -> amount.signed()
                    },
                    when {
                        amount == null || amount.isNegative -> colors.danger
                        else -> colors.textIncome
                    },
                )
                ZarDivider()
                val approximate = portfolio.assets.firstOrNull { it.currency == out.amount.currency }?.isCostApproximate == true
                CryptoDetailRow(
                    stringResource(R.string.crypto_detail_cost_method),
                    stringResource(if (approximate) R.string.crypto_cost_method_approximate else R.string.crypto_cost_method),
                )
            }
            ZarDivider()
            CryptoDetailRow(stringResource(R.string.crypto_detail_note), out.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.crypto_not_income))
        }

        BillCard(Modifier.padding(top = 10.dp).testTag("crypto-linked")) {
            ZarEyebrow(stringResource(R.string.crypto_linked), Modifier.padding(top = 12.dp))
            LinkedEntry(
                title = stringResource(R.string.crypto_out_of, row.fromName),
                account = accounts[out.accountId],
                currencyCode = out.amount.currency.code,
                amount = "− ${out.amount.withCode()}",
                incoming = false,
            )
            LinkedEntry(
                title = stringResource(R.string.crypto_into, row.toName),
                account = accounts[into.accountId],
                currencyCode = into.amount.currency.code,
                amount = "+ ${into.amount.withCode()}",
                incoming = true,
            )
            Text(
                stringResource(
                    when (row.kind) {
                        CryptoMovementKind.BUY -> R.string.crypto_linked_note_buy
                        CryptoMovementKind.SELL -> R.string.crypto_linked_note_sell
                        CryptoMovementKind.TRADE -> R.string.crypto_linked_note_trade
                        CryptoMovementKind.MOVE -> R.string.crypto_linked_note_move
                    },
                ),
                style = ZarType.bodyCaption.copy(lineHeight = 15.sp),
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
    }
}

/** One side of the movement. [account] is set only for crypto wallets; anything else is a money account. */
@Composable
private fun LinkedEntry(title: String, account: Account?, currencyCode: String, amount: String, incoming: Boolean) {
    val colors = ZarTheme.colors
    val isWallet = account?.type == AccountType.CRYPTO
    CryptoRow(
        icon = when {
            isWallet -> currencyIcon(account!!.currency)
            incoming -> R.drawable.ic_ph_arrow_down_left
            else -> R.drawable.ic_ph_arrowupright
        },
        title = title,
        lines = listOf(
            stringResource(if (isWallet) R.string.crypto_wallet_meta else R.string.crypto_money_meta, currencyCode) to null,
        ),
        end = amount,
        endColor = if (incoming) colors.textIncome else null,
    )
}
