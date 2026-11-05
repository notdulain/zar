package com.example.zar.features.crypto.presentation.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.R
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.privacy.amountFieldTransformation
import com.example.zar.core.presentation.theme.ZarGradients
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.bills.presentation.screen.StatusDot
import com.example.zar.features.transactions.presentation.income.IncomeFormat

/** The gold dot on the crypto hero's status line. */
internal val CryptoGold = Color(0xFFE0B54A)

/** "BTC 0.005", "LKR 360,000". */
internal fun Money.withCode(): String = IncomeFormat.withCode(this)

/** "+ LKR 40,000", "− LKR 10,000" or "LKR 0". */
internal fun FiatMoney.signed(): String = when {
    isPositive -> "+ ${withCode()}"
    isNegative -> "− ${FiatMoney(-minorUnits, currency).withCode()}"
    else -> withCode()
}

@DrawableRes
internal fun currencyIcon(currency: Currency): Int = if (currency == Currencies.BTC) R.drawable.ic_ph_currency_btc else R.drawable.ic_ph_wallet

/** Icon for an account in a movement: its coin for a crypto wallet, a bank for a money account. */
@DrawableRes
internal fun accountIcon(type: AccountType, currency: Currency): Int =
    if (type == AccountType.CRYPTO) currencyIcon(currency) else R.drawable.ic_ph_bank

/** Z89 hero: back, title, the realized total in LKR, a caption and a one-line status with a gold dot. */
@Composable
internal fun CryptoHero(
    amount: String,
    caption: String,
    status: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Moves the title, amount and status together, e.g. a slower drift and fade while the page scrolls. */
    contentModifier: Modifier = Modifier,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val onDark = Color.White
    Column(
        modifier.fillMaxWidth()
            .clip(ZarShapes.hero)
            .background(ZarGradients.crypto)
            .padding(top = maxOf(54.dp, statusBar + 4.dp), bottom = 22.dp)
            .testTag("crypto-hero"),
    ) {
        Box(
            Modifier.padding(start = 14.dp).size(44.dp).clip(ZarShapes.full)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.crypto_back), onClick = onBack),
            Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_ph_arrow_left), stringResource(R.string.crypto_back), Modifier.size(22.dp), tint = onDark)
        }
        Column(contentModifier.padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.crypto_title), style = ZarType.displayTitle, color = onDark, modifier = Modifier.padding(top = 14.dp))
            ZarEyebrow(stringResource(R.string.crypto_realized_label), Modifier.padding(top = 26.dp), color = onDark.copy(alpha = 0.6f))
            Text(amount, style = ZarType.amountL.copy(lineHeight = 40.sp), color = onDark, modifier = Modifier.padding(top = 10.dp).testTag("crypto-realized"))
            Text(caption, style = ZarType.bodyS.copy(lineHeight = 19.sp), color = onDark.copy(alpha = 0.8f), modifier = Modifier.padding(top = 12.dp).width(260.dp))
            if (status != null) {
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(CryptoGold)
                    Text(status, style = ZarType.bodyCaption, color = onDark.copy(alpha = 0.9f), modifier = Modifier.testTag("crypto-status"))
                }
            }
        }
    }
}

/** Icon chip, a title and caption lines, and an optional value at the end. */
@Composable
internal fun CryptoRow(
    @DrawableRes icon: Int,
    title: String,
    lines: List<Pair<String, Color?>>,
    modifier: Modifier = Modifier,
    end: String? = null,
    endColor: Color? = null,
    onEndClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = ZarTheme.colors
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZarIconChip(icon, size = 36.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = ZarType.labelButton.copy(lineHeight = 19.sp), color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            lines.forEach { (text, color) ->
                Text(text, style = ZarType.bodyCaption, color = color ?: colors.textSecondary)
            }
        }
        if (end != null) {
            Text(
                end,
                style = if (onEndClick != null) ZarType.labelAction else ZarType.bodyM,
                color = endColor ?: if (onEndClick != null) colors.textSelected else colors.textPrimary,
                maxLines = 1,
                modifier = if (onEndClick != null) Modifier.clip(ZarShapes.xs).clickable(role = Role.Button, onClick = onEndClick).padding(8.dp) else Modifier,
            )
        }
    }
}

/** A Figma detail line: eyebrow label (110dp) and a value, optionally coloured. */
@Composable
internal fun CryptoDetailRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(110.dp)) { ZarEyebrow(label) }
        Text(value, style = ZarType.bodyM.copy(lineHeight = 19.sp), color = valueColor ?: ZarTheme.colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

/** Large light amount typed in place, with its placeholder. */
@Composable
internal fun AmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "0",
    isError: Boolean = false,
    textStyle: TextStyle = ZarType.amountM,
) {
    val colors = ZarTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle.copy(color = if (isError) colors.danger else colors.textPrimary),
        cursorBrush = SolidColor(colors.textSelected),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = amountFieldTransformation(),
        modifier = modifier,
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 6.dp), Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.textPlaceholder)
                inner()
            }
        },
    )
}

/** Red Body/Caption error under a field. */
@Composable
internal fun FieldError(text: String?, modifier: Modifier = Modifier) {
    if (text != null) Text(text, style = ZarType.bodyCaption, color = ZarTheme.colors.danger, modifier = modifier)
}
