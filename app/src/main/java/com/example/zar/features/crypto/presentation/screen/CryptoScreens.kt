package com.example.zar.features.crypto.presentation.screen

import com.example.zar.core.presentation.privacy.amountFieldTransformation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarHeroItemKey
import com.example.zar.core.presentation.components.heroDisplacement
import com.example.zar.core.presentation.components.heroParallax
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.features.crypto.domain.CryptoProblem
import com.example.zar.features.crypto.domain.model.AssetPosition
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import com.example.zar.features.crypto.domain.model.CryptoMovementRow
import com.example.zar.features.crypto.domain.model.CryptoPortfolio
import com.example.zar.features.crypto.presentation.viewmodel.CryptoMessage
import com.example.zar.features.crypto.presentation.viewmodel.CryptoMovementViewModel
import com.example.zar.features.crypto.presentation.viewmodel.CryptoUiState
import com.example.zar.features.crypto.presentation.viewmodel.CryptoViewModel
import com.example.zar.features.freelance.presentation.screen.CenteredText
import com.example.zar.features.freelance.presentation.screen.DateField
import com.example.zar.features.freelance.presentation.screen.Loading
import com.example.zar.features.freelance.presentation.screen.SectionLabel
import com.example.zar.features.freelance.presentation.screen.display
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.screen.SummaryHeaderCard
import com.example.zar.features.transactions.presentation.income.screen.ZarMono

/** "+ LKR 40,000", "− LKR 10,000" or "LKR 0". */
private fun FiatMoney.signed(): String = when {
    isPositive -> "+ ${display()}"
    isNegative -> "− ${FiatMoney(-minorUnits, currency).display()}"
    else -> display()
}

private fun CryptoMessage.textRes(): Int = when (this) {
    CryptoMessage.NOT_SIGNED_IN -> R.string.crypto_message_not_signed_in
    CryptoMessage.FAILED -> R.string.crypto_message_failed
}

// --- Overview ------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoScreen(
    onBack: () -> Unit,
    onAddMovement: () -> Unit,
    viewModel: CryptoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var undoing by remember { mutableStateOf<CryptoMovementRow?>(null) }
    val portfolio = uiState.portfolio
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crypto_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            if (portfolio?.wallets?.isNotEmpty() == true) {
                val addDescription = stringResource(R.string.crypto_add_movement)
                FloatingActionButton(
                    onClick = onAddMovement,
                    modifier = Modifier.semantics { contentDescription = addDescription },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ) { Text("+", fontSize = 28.sp) }
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Loading(Modifier.padding(innerPadding))
            uiState.loadFailed || portfolio == null -> CenteredText(stringResource(R.string.crypto_load_failed), Modifier.padding(innerPadding))
            else -> CryptoContent(portfolio, uiState, viewModel, onUndo = { undoing = it }, modifier = Modifier.padding(innerPadding))
        }
    }

    uiState.editingCostFor?.let { wallet -> OpeningCostDialog(wallet, uiState, viewModel) }
    undoing?.let { row ->
        AlertDialog(
            onDismissRequest = { undoing = null },
            title = { Text(stringResource(R.string.crypto_undo_title)) },
            text = { Text(stringResource(R.string.crypto_undo_body)) },
            confirmButton = {
                TextButton(onClick = {
                    undoing = null
                    viewModel.undo(row)
                }) { Text(stringResource(R.string.crypto_undo), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { undoing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CryptoContent(
    portfolio: CryptoPortfolio,
    uiState: CryptoUiState,
    viewModel: CryptoViewModel,
    onUndo: (CryptoMovementRow) -> Unit,
    modifier: Modifier,
) {
    val cryptoListState = rememberLazyListState()
    val cryptoHeroOffset = cryptoListState.heroDisplacement()
    LazyColumn(
        state = cryptoListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = ZarHeroItemKey) {
            SummaryHeaderCard(
                label = stringResource(R.string.crypto_realized_label),
                amount = portfolio.realized.signed(),
                caption = if (portfolio.unknownResultCount == 0) {
                    stringResource(R.string.crypto_realized_caption)
                } else {
                    pluralStringResource(R.plurals.crypto_unknown_results, portfolio.unknownResultCount, portfolio.unknownResultCount)
                },
                modifier = Modifier.heroParallax(cryptoHeroOffset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap),
            )
        }
        uiState.message?.let { item { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono) } }
        if (portfolio.wallets.isEmpty()) {
            item { CenteredText(stringResource(R.string.crypto_no_wallets), Modifier.padding(top = 32.dp)) }
            return@LazyColumn
        }

        item { SectionLabel(stringResource(R.string.crypto_holdings), Modifier.padding(top = 8.dp)) }
        items(portfolio.assets, key = { "asset-${it.currency.code}" }) { AssetCard(it) }

        item { SectionLabel(stringResource(R.string.crypto_wallets), Modifier.padding(top = 8.dp)) }
        items(portfolio.wallets, key = { "wallet-${it.id.value}" }) { wallet ->
            WalletCard(wallet, portfolio.openingCosts[wallet.id]?.let { it.cost to it.isApproximate }, onSetCost = { viewModel.editCost(wallet) })
        }

        item { SectionLabel(stringResource(R.string.crypto_movements), Modifier.padding(top = 8.dp)) }
        if (portfolio.movements.isEmpty()) item { CenteredText(stringResource(R.string.crypto_no_movements)) }
        items(portfolio.movements, key = { it.outEntry.id.value }) { row -> MovementCard(row, onClick = { onUndo(row) }) }
    }
}

@Composable
private fun AssetCard(position: AssetPosition) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(position.currency.code, style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono, modifier = Modifier.weight(1f))
                val quantity = position.quantity.display()
                Text(
                    if (position.isQuantityComplete) quantity else stringResource(R.string.crypto_at_least, quantity),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = ZarMono,
                )
            }
            val costLine = listOfNotNull(
                if (position.knownCost.isPositive) stringResource(R.string.crypto_cost, position.knownCost.display()) else null,
                position.averageCost?.let { stringResource(R.string.crypto_avg_cost, it.display()) },
                if (position.isCostApproximate) stringResource(R.string.crypto_approximate) else null,
            )
            if (costLine.isNotEmpty()) Detail(costLine.joinToString(" · "))
            if (position.unknownCostQuantity.isPositive) {
                Detail(stringResource(R.string.crypto_cost_unknown_for, position.unknownCostQuantity.display()), warning = true)
            }
            if (!position.realized.isZero) Detail(stringResource(R.string.crypto_realized_asset, position.realized.signed()))
        }
    }
}

@Composable
private fun WalletCard(wallet: Account, cost: Pair<FiatMoney, Boolean>?, onSetCost: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(wallet.name, style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono)
                val opening = when (val balance = wallet.openingBalance) {
                    is OpeningBalance.Known -> stringResource(R.string.crypto_opening, balance.amount.display())
                    is OpeningBalance.Approximate -> stringResource(R.string.crypto_opening, balance.amount.display()) + " (" + stringResource(R.string.crypto_approximate) + ")"
                    OpeningBalance.Unknown -> stringResource(R.string.crypto_opening_unknown)
                }
                val costText = cost?.let { (amount, approximate) ->
                    stringResource(R.string.crypto_opening_cost, amount.display()) + if (approximate) " (" + stringResource(R.string.crypto_approximate) + ")" else ""
                } ?: stringResource(R.string.crypto_opening_cost_missing)
                Detail("$opening · $costText", warning = cost == null && wallet.openingBalance !is OpeningBalance.Unknown && hasCoins(wallet))
            }
            TextButton(onClick = onSetCost) { Text(stringResource(R.string.crypto_set_cost)) }
        }
    }
}

private fun hasCoins(wallet: Account): Boolean = when (val balance = wallet.openingBalance) {
    is OpeningBalance.Known -> balance.amount.isPositive
    is OpeningBalance.Approximate -> balance.amount.isPositive
    OpeningBalance.Unknown -> false
}

@Composable
private fun MovementCard(row: CryptoMovementRow, onClick: () -> Unit) {
    val out = row.outEntry.current
    val inTx = row.inEntry.current
    val title = when (row.kind) {
        CryptoMovementKind.BUY -> stringResource(R.string.crypto_kind_buy, inTx.amount.currency.code)
        CryptoMovementKind.SELL -> stringResource(R.string.crypto_kind_sell, out.amount.currency.code)
        CryptoMovementKind.TRADE -> stringResource(R.string.crypto_kind_trade, out.amount.currency.code, inTx.amount.currency.code)
        CryptoMovementKind.MOVE -> stringResource(R.string.crypto_kind_move, out.amount.currency.code)
    }
    val value = CurrencyConverter.reportingAmount(out.amount, out.reportingConversion)
        ?: CurrencyConverter.reportingAmount(inTx.amount, inTx.reportingConversion)
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono)
                Detail("${row.fromName} → ${row.toName} · ${IncomeFormat.shortDate(row.date)}")
                Detail(value?.display() ?: stringResource(R.string.crypto_no_value), warning = value == null && row.kind != CryptoMovementKind.MOVE)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("− ${out.amount.display()}", style = MaterialTheme.typography.bodySmall, fontFamily = ZarMono)
                Text("+ ${inTx.amount.display()}", style = MaterialTheme.typography.bodySmall, fontFamily = ZarMono)
                row.result?.let { result ->
                    val amount = result.amount
                    val text = when {
                        amount == null -> stringResource(R.string.crypto_result_unknown)
                        amount.isNegative -> stringResource(R.string.crypto_loss, FiatMoney(-amount.minorUnits, amount.currency).display())
                        else -> stringResource(R.string.crypto_profit, amount.display())
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ZarMono,
                        color = if (amount == null || amount.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Detail(text: String, warning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = ZarMono,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OpeningCostDialog(wallet: Account, uiState: CryptoUiState, viewModel: CryptoViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissCost,
        title = { Text(stringResource(R.string.crypto_cost_title, wallet.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.crypto_cost_body), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = uiState.costText,
                    onValueChange = viewModel::onCostChange,
                    label = { Text(stringResource(R.string.crypto_cost_field)) },
                    prefix = { Text("LKR ", fontFamily = ZarMono) },
                    isError = uiState.costInvalid,
                    supportingText = if (uiState.costInvalid) {
                        { Text(stringResource(R.string.crypto_cost_invalid)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = amountFieldTransformation(),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uiState.costApproximate, onCheckedChange = viewModel::onCostApproximateChange)
                    Text(stringResource(R.string.crypto_cost_approximate))
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveCost, enabled = !uiState.isSaving) { Text(stringResource(R.string.income_save)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissCost) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// --- Movement form -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoMovementScreen(
    onDone: () -> Unit,
    viewModel: CryptoMovementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isFinished) { if (uiState.isFinished) onDone() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crypto_movement_title)) },
                navigationIcon = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Loading(Modifier.padding(innerPadding))
            return@Scaffold
        }
        val problems = uiState.problems
        Column(
            Modifier.fillMaxSize().padding(innerPadding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountChooser(
                label = stringResource(R.string.crypto_from),
                accounts = uiState.accounts,
                selected = uiState.fromId,
                error = when {
                    CryptoProblem.FROM_MISSING in problems -> stringResource(R.string.crypto_error_account)
                    else -> null
                },
                onSelect = viewModel::onFromSelect,
            )
            AccountChooser(
                label = stringResource(R.string.crypto_to),
                accounts = uiState.accounts,
                selected = uiState.toId,
                error = when {
                    CryptoProblem.TO_MISSING in problems -> stringResource(R.string.crypto_error_account)
                    CryptoProblem.SAME_ACCOUNT in problems -> stringResource(R.string.crypto_error_same)
                    CryptoProblem.NO_CRYPTO_ACCOUNT in problems -> stringResource(R.string.crypto_error_no_crypto)
                    else -> null
                },
                onSelect = viewModel::onToSelect,
            )

            uiState.kind?.let { kind ->
                Text(
                    stringResource(
                        when (kind) {
                            CryptoMovementKind.BUY -> R.string.crypto_explain_buy
                            CryptoMovementKind.SELL -> R.string.crypto_explain_sell
                            CryptoMovementKind.TRADE -> R.string.crypto_explain_trade
                            CryptoMovementKind.MOVE -> R.string.crypto_explain_move
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AmountField(
                label = stringResource(R.string.crypto_sent),
                value = uiState.sent,
                code = uiState.from?.currency?.code,
                error = amountError(problems, CryptoProblem.SENT_MISSING, CryptoProblem.SENT_INVALID),
                hint = null,
                onChange = viewModel::onSentChange,
            )
            AmountField(
                label = stringResource(R.string.crypto_received),
                value = uiState.received,
                code = uiState.to?.currency?.code,
                error = amountError(problems, CryptoProblem.RECEIVED_MISSING, CryptoProblem.RECEIVED_INVALID),
                hint = if (uiState.kind == CryptoMovementKind.MOVE) stringResource(R.string.crypto_received_same) else null,
                onChange = viewModel::onReceivedChange,
            )
            if (uiState.showValue) {
                AmountField(
                    label = stringResource(R.string.crypto_value),
                    value = uiState.lkrValue,
                    code = "LKR",
                    error = if (CryptoProblem.VALUE_INVALID in problems) stringResource(R.string.crypto_error_value_invalid) else null,
                    hint = stringResource(R.string.crypto_value_hint),
                    onChange = viewModel::onValueChange,
                )
            }
            DateField(stringResource(R.string.crypto_date), uiState.date, enabled = true, onSelect = viewModel::onDateSelect)
            if (CryptoProblem.DATE_IN_FUTURE in problems) Text(stringResource(R.string.crypto_error_date_future), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text(stringResource(R.string.crypto_note)) },
                isError = CryptoProblem.NOTE_TOO_LONG in problems,
                supportingText = if (CryptoProblem.NOTE_TOO_LONG in problems) {
                    { Text(stringResource(R.string.crypto_error_too_long)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            uiState.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono) }
            Button(onClick = viewModel::save, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.income_save))
            }
        }
    }
}

@Composable
private fun amountError(problems: Set<CryptoProblem>, missing: CryptoProblem, invalid: CryptoProblem): String? = when {
    missing in problems -> stringResource(R.string.crypto_error_amount_missing)
    invalid in problems -> stringResource(R.string.crypto_error_amount_invalid)
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChooser(label: String, accounts: List<Account>, selected: AccountId?, error: String?, onSelect: (AccountId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { account ->
                FilterChip(
                    selected = account.id == selected,
                    onClick = { onSelect(account.id) },
                    label = { Text("${account.name} · ${account.currency.code}", fontFamily = ZarMono) },
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun AmountField(label: String, value: String, code: String?, error: String?, hint: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        prefix = code?.let { { Text("$it ", fontFamily = ZarMono) } },
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = amountFieldTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
