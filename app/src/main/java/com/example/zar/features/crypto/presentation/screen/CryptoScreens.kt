package com.example.zar.features.crypto.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.components.ZarAddPill
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.bills.presentation.screen.BillCard
import com.example.zar.features.bills.presentation.screen.BillNote
import com.example.zar.features.bills.presentation.screen.BillSectionHeader
import com.example.zar.features.bills.presentation.screen.StatusBarGlyphs
import com.example.zar.features.bills.presentation.screen.BillFormat
import com.example.zar.features.crypto.domain.model.AssetPosition
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import com.example.zar.features.crypto.domain.model.CryptoMovementRow
import com.example.zar.features.crypto.domain.model.CryptoPortfolio
import com.example.zar.features.crypto.domain.model.OpeningCost
import com.example.zar.features.crypto.presentation.model.CryptoHeroStatus
import com.example.zar.features.crypto.presentation.model.CryptoOverview
import com.example.zar.features.crypto.presentation.viewmodel.CryptoMessage
import com.example.zar.features.crypto.presentation.viewmodel.CryptoUiState
import com.example.zar.features.crypto.presentation.viewmodel.CryptoViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat

internal fun CryptoMessage.textRes(): Int = when (this) {
    CryptoMessage.NOT_SIGNED_IN -> R.string.crypto_message_not_signed_in
    CryptoMessage.FAILED -> R.string.crypto_message_failed
}

/** Z89–Z93 — Crypto: realized profit or loss, holdings, wallets and movements, with the record sheet on top. */
@Composable
fun CryptoScreen(
    onBack: () -> Unit,
    onOpenMovement: (String) -> Unit,
    onAddAccount: () -> Unit,
    viewModel: CryptoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Each opening gets a fresh form (and a fresh transfer id), so one sheet can never save twice.
    var recordSession by rememberSaveable { mutableStateOf<Int?>(null) }
    var nextSession by rememberSaveable { mutableIntStateOf(0) }
    CryptoContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenMovement = { onOpenMovement(it.outEntry.id.value) },
        onSetCost = viewModel::editCost,
        onRecord = { recordSession = nextSession++ },
        onAddAccount = onAddAccount,
    )
    uiState.editingCostFor?.let { wallet ->
        OpeningCostSheet(
            wallet = wallet,
            uiState = uiState,
            onCostChange = viewModel::onCostChange,
            onApproximateChange = viewModel::onCostApproximateChange,
            onSave = viewModel::saveCost,
            onDismiss = viewModel::dismissCost,
        )
    }
    recordSession?.let { session -> RecordMovementSheet(session, onDismiss = { recordSession = null }) }
}

@Composable
internal fun CryptoContent(
    uiState: CryptoUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenMovement: (CryptoMovementRow) -> Unit,
    onSetCost: (Account) -> Unit,
    onRecord: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val colors = ZarTheme.colors
    val scroll = rememberScrollState()
    var heroHeight by remember { mutableIntStateOf(0) }
    val pastHero by remember { derivedStateOf { heroHeight > 0 && scroll.value > heroHeight - 120 } }
    StatusBarGlyphs(onDarkHero = !pastHero)
    val portfolio = uiState.portfolio
    val failed = uiState.loadFailed && portfolio == null

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Hero(portfolio, failed, onBack, Modifier.onSizeChanged { heroHeight = it.height })
            when {
                uiState.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 72.dp), Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentSelected)
                }
                failed -> NoticeCard(
                    icon = R.drawable.ic_ph_wifi_high,
                    title = stringResource(R.string.crypto_load_failed_title),
                    body = stringResource(R.string.crypto_load_failed_body),
                    modifier = Modifier.padding(top = 22.dp).testTag("crypto-load-failed"),
                )
                portfolio == null -> Unit
                portfolio.wallets.isEmpty() -> NoticeCard(
                    icon = R.drawable.ic_ph_wallet,
                    title = stringResource(R.string.crypto_no_wallets_title),
                    body = stringResource(R.string.crypto_no_wallets_body),
                    modifier = Modifier.padding(top = 22.dp).testTag("crypto-no-wallets"),
                    card = false,
                )
                else -> Sections(portfolio, uiState.message, onOpenMovement, onSetCost)
            }
            // Room so the last card clears the floating button.
            Spacer(Modifier.height(124.dp))
        }
        // Once the dark hero has scrolled away, the canvas fades in under the status bar so the clock never sits on a card.
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            Modifier.fillMaxWidth().height(statusBar + 28.dp)
                .graphicsLayer { alpha = if (heroHeight == 0) 0f else ((scroll.value - (heroHeight - 160)) / 60f).coerceIn(0f, 1f) }
                .background(Brush.verticalGradient(0f to colors.canvas, 0.65f to colors.canvas, 1f to colors.canvas.copy(alpha = 0f))),
        )
        val bottom = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp)
        when {
            uiState.isLoading -> Unit
            failed -> ZarButton(stringResource(R.string.crypto_try_again), onClick = onRetry, modifier = bottom.testTag("crypto-retry"))
            portfolio?.wallets?.isEmpty() == true ->
                ZarButton(stringResource(R.string.crypto_add_account), onClick = onAddAccount, modifier = bottom.testTag("crypto-add-account"))
            portfolio != null -> ZarAddPill(stringResource(R.string.crypto_record_movement), onClick = onRecord, modifier = bottom.testTag("crypto-record"))
        }
    }
}

@Composable
private fun Hero(portfolio: CryptoPortfolio?, failed: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    if (failed || portfolio == null) {
        CryptoHero(
            amount = if (failed) "—" else "",
            caption = if (failed) stringResource(R.string.crypto_unavailable) else "",
            status = if (failed) stringResource(R.string.crypto_status_connection) else null,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }
    val status = CryptoOverview.heroStatus(portfolio)
    CryptoHero(
        amount = portfolio.realized.signed(),
        caption = if (portfolio.unknownResultCount == 0) {
            stringResource(R.string.crypto_realized_caption)
        } else {
            pluralStringResource(R.plurals.crypto_unknown_results, portfolio.unknownResultCount, portfolio.unknownResultCount)
        },
        status = when (status) {
            CryptoHeroStatus.NoWallets -> stringResource(R.string.crypto_status_no_wallets)
            CryptoHeroStatus.OpeningBalancesOnly -> stringResource(R.string.crypto_status_opening_only)
            CryptoHeroStatus.NoSalesYet -> stringResource(R.string.crypto_status_no_sales)
            CryptoHeroStatus.KnownResultsOnly -> stringResource(R.string.crypto_status_known_only)
            is CryptoHeroStatus.Realized -> pluralStringResource(
                if (status.approximate) R.plurals.crypto_status_realized_approximate else R.plurals.crypto_status_realized,
                status.sales,
                status.sales,
            )
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun Sections(
    portfolio: CryptoPortfolio,
    message: CryptoMessage?,
    onOpenMovement: (CryptoMovementRow) -> Unit,
    onSetCost: (Account) -> Unit,
) {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        message?.let { FieldError(stringResource(it.textRes()), Modifier.padding(horizontal = 22.dp)) }
        if (portfolio.assets.isNotEmpty()) {
            BillSectionHeader(stringResource(R.string.crypto_holdings))
            portfolio.assets.forEach { HoldingCard(it) }
            Spacer(Modifier.height(13.dp))
        }

        BillSectionHeader(stringResource(R.string.crypto_wallets))
        BillCard(Modifier.testTag("crypto-wallets")) {
            portfolio.wallets.forEachIndexed { index, wallet ->
                if (index > 0) ZarDivider()
                WalletRow(wallet, portfolio.openingCosts[wallet.id], onSetCost = { onSetCost(wallet) })
            }
        }
        Spacer(Modifier.height(13.dp))

        BillSectionHeader(stringResource(R.string.crypto_movements))
        if (portfolio.movements.isEmpty()) {
            BillNote(stringResource(R.string.crypto_no_movements))
        } else {
            portfolio.movements.forEach { row -> MovementCard(row, onClick = { onOpenMovement(row) }) }
        }
    }
}

/** Z89 holding: coin, quantity, and what it cost on average, or a warning when part of the cost is unknown. */
@Composable
private fun HoldingCard(position: AssetPosition) {
    val colors = ZarTheme.colors
    val code = position.currency.code
    val quantity = position.quantity.withCode()
    BillCard(Modifier.testTag("crypto-holding-$code")) {
        CryptoRow(
            icon = currencyIcon(position.currency),
            title = code,
            lines = listOf(
                stringResource(if (position.isCostApproximate) R.string.crypto_approximate else currencyName(code)) to null,
            ),
            end = if (position.isQuantityComplete) quantity else stringResource(R.string.crypto_at_least, quantity),
        )
        Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (position.unknownCostQuantity.isPositive) {
                Text(
                    stringResource(R.string.crypto_cost_unknown_for, position.unknownCostQuantity.withCode()),
                    style = ZarType.bodyCaption.copy(lineHeight = 15.sp),
                    color = colors.danger,
                )
            }
            if (position.knownCost.isPositive) Caption(stringResource(R.string.crypto_cost, position.knownCost.withCode()))
            position.averageCost?.let { Caption(stringResource(R.string.crypto_avg_cost, it.withCode(), code)) }
        }
    }
}

private fun currencyName(code: String): Int = when (code) {
    "BTC" -> R.string.crypto_name_btc
    "ETH" -> R.string.crypto_name_eth
    "USDT" -> R.string.crypto_name_usdt
    else -> R.string.crypto_name_other
}

@Composable
private fun Caption(text: String) {
    Text(text, style = ZarType.bodyCaption.copy(lineHeight = 15.sp), color = ZarTheme.colors.textSecondary)
}

/** Z89/Z90 wallet: what it opened with and what that cost; "Set cost" when coins have no cost yet. */
@Composable
private fun WalletRow(wallet: Account, cost: OpeningCost?, onSetCost: () -> Unit) {
    val colors = ZarTheme.colors
    val approximate = stringResource(R.string.crypto_approximate)
    val opening = when (val balance = wallet.openingBalance) {
        is OpeningBalance.Known -> stringResource(R.string.crypto_opening, balance.amount.withCode())
        is OpeningBalance.Approximate -> stringResource(R.string.crypto_opening, balance.amount.withCode()) + " · " + approximate
        OpeningBalance.Unknown -> stringResource(R.string.crypto_opening_unknown)
    }
    val missing = cost == null && hasCoins(wallet)
    val costLine = when {
        cost != null -> stringResource(R.string.crypto_opening_cost, cost.cost.withCode())
        missing -> stringResource(R.string.crypto_opening_cost_missing)
        else -> stringResource(R.string.crypto_opening_cost, FiatMoney(0, Currencies.LKR).withCode())
    }
    CryptoRow(
        icon = R.drawable.ic_ph_wallet,
        title = wallet.name,
        lines = listOf(opening to if (missing) colors.danger else null, costLine to if (missing) colors.danger else null),
        end = if (missing) stringResource(R.string.crypto_set_cost) else null,
        onEndClick = if (missing) onSetCost else null,
        onClick = onSetCost,
        modifier = Modifier.testTag("crypto-wallet-${wallet.id.value}"),
    )
}

private fun hasCoins(wallet: Account): Boolean = when (val balance = wallet.openingBalance) {
    is OpeningBalance.Known -> balance.amount.isPositive
    is OpeningBalance.Approximate -> balance.amount.isPositive
    OpeningBalance.Unknown -> false
}

@Composable
internal fun movementTitle(row: CryptoMovementRow): String {
    val out = row.outEntry.current.amount.currency.code
    val into = row.inEntry.current.amount.currency.code
    return when (row.kind) {
        CryptoMovementKind.BUY -> stringResource(R.string.crypto_kind_buy, into)
        CryptoMovementKind.SELL -> stringResource(R.string.crypto_kind_sell, out)
        CryptoMovementKind.TRADE -> stringResource(R.string.crypto_kind_trade, out, into)
        CryptoMovementKind.MOVE -> stringResource(R.string.crypto_kind_move, out)
    }
}

/** Z89b movement: what it was, between which accounts, both amounts, and the profit or loss for a sale. */
@Composable
private fun MovementCard(row: CryptoMovementRow, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val amounts = stringResource(R.string.crypto_movement_amounts, row.outEntry.current.amount.withCode(), row.inEntry.current.amount.withCode())
    val result = row.result?.let { r ->
        val amount = r.amount
        when {
            amount == null -> stringResource(R.string.crypto_result_unknown) to colors.danger
            amount.isNegative -> stringResource(R.string.crypto_loss, FiatMoney(-amount.minorUnits, amount.currency).withCode()) to colors.danger
            else -> stringResource(R.string.crypto_profit, amount.withCode()) to colors.textIncome
        }
    }
    val noValue = if (row.result == null && row.kind != CryptoMovementKind.MOVE && CryptoOverview.lkrValue(row) == null) {
        stringResource(R.string.crypto_no_value) to colors.danger
    } else null
    ZarCard(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().testTag("crypto-movement"),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        CryptoRow(
            icon = R.drawable.ic_ph_arrows_left_right,
            title = movementTitle(row),
            lines = listOf("${row.fromName} → ${row.toName} · ${BillFormat.dayMonth(row.date)}" to null),
        )
        Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(amounts, style = ZarType.bodyS.copy(lineHeight = 18.sp), color = colors.textPrimary)
            listOfNotNull(result, noValue).forEach { (text, color) ->
                Text(text, style = ZarType.bodyCaption.copy(lineHeight = 15.sp), color = color)
            }
        }
    }
}

/** Z91/Z93: an icon, a short title and one sentence, on a soft card or straight on the canvas. */
@Composable
private fun NoticeCard(icon: Int, title: String, body: String, modifier: Modifier = Modifier, card: Boolean = true) {
    val colors = ZarTheme.colors
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ZarIcon(icon, size = 18.dp, tint = colors.iconMuted, modifier = if (card) Modifier.align(Alignment.CenterHorizontally) else Modifier)
            Text(title, style = ZarType.bodyL.copy(lineHeight = 22.sp), color = colors.textPrimary, modifier = Modifier.padding(top = 6.dp))
            Text(body, style = ZarType.bodyS.copy(lineHeight = 19.sp), color = colors.textSecondary)
        }
    }
    if (card) {
        Box(modifier.padding(horizontal = 10.dp).fillMaxWidth().background(colors.subtle, ZarShapes.lg)) { content() }
    } else {
        Box(modifier.padding(horizontal = 10.dp).fillMaxWidth()) { content() }
    }
}
