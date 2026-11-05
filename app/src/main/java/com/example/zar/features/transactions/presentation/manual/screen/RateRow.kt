package com.example.zar.features.transactions.presentation.manual.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.rates.domain.RateHint
import com.example.zar.features.rates.domain.RateEntry
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The provider's terms ask for a visible link wherever its rate is shown. */
internal const val RATE_ATTRIBUTION_URL = "https://www.exchangerate-api.com"

/**
 * The rate field of the income and expense forms: "1 USD = ? LKR", the number, a link to fill in today's rate, and one
 * line saying where the number came from. Shared so both forms behave the same (Figma Z198 to Z210).
 */
@Composable
internal fun RateRow(
    currency: Currency?,
    entry: RateEntry,
    canUseTodaysRate: Boolean,
    invalid: Boolean,
    onRateChange: (String) -> Unit,
    onUseTodaysRate: () -> Unit,
    inputTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    val hint = entry.hint(canUseTodaysRate)
    val uriHandler = LocalUriHandler.current
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ZarEyebrow(stringResource(R.string.income_field_rate, currency?.code.orEmpty()))
            when {
                hint == RateHint.FETCHING ->
                    Text(stringResource(R.string.rate_getting), style = ZarType.labelAction, color = colors.textTertiary, modifier = Modifier.testTag("rate-getting"))
                canUseTodaysRate && (hint == RateHint.AVAILABLE || hint == RateHint.TYPED_OVER) ->
                    Text(
                        stringResource(R.string.rate_use_today), style = ZarType.labelAction, color = colors.textSelected,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onUseTodaysRate).testTag("rate-use-today"),
                    )
                canUseTodaysRate && hint == RateHint.FAILED ->
                    Text(
                        stringResource(R.string.rate_try_again), style = ZarType.labelAction, color = colors.textSelected,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onUseTodaysRate).testTag("rate-try-again"),
                    )
            }
        }
        TextInput(
            value = entry.text,
            onValueChange = onRateChange,
            placeholder = "0",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth().testTag(inputTag),
        )
        if (invalid) {
            Text(stringResource(R.string.income_error_rate_invalid), style = ZarType.bodyCaption, color = colors.danger)
        } else {
            Text(
                rateHintText(hint, entry, locale),
                style = ZarType.bodyCaption,
                color = if (hint == RateHint.FAILED) colors.danger else colors.textSecondary,
                modifier = Modifier.testTag("rate-hint"),
            )
            if (hint == RateHint.FILLED || hint == RateHint.TYPED_OVER || hint == RateHint.SAVED_FROM_PROVIDER) {
                Text(
                    stringResource(R.string.rate_attribution),
                    style = ZarType.bodyCaption.copy(textDecoration = TextDecoration.Underline),
                    color = colors.textSelected,
                    modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri(RATE_ATTRIBUTION_URL) }.testTag("rate-attribution"),
                )
            }
        }
    }
}

@Composable
private fun rateHintText(hint: RateHint, entry: RateEntry, locale: Locale): String = when (hint) {
    RateHint.AVAILABLE -> stringResource(R.string.rate_hint_available)
    RateHint.FETCHING -> stringResource(R.string.rate_hint_fetching)
    RateHint.FAILED -> stringResource(R.string.rate_hint_failed)
    RateHint.FILLED -> stringResource(R.string.rate_hint_filled, publishedAt(entry.providerQuoteInUse?.updatedAt, locale))
    RateHint.TYPED_OVER -> stringResource(R.string.rate_hint_typed, entry.quote?.fieldText.orEmpty())
    RateHint.SAVED_FROM_PROVIDER -> stringResource(R.string.rate_hint_saved, publishedAt(entry.savedFromProvider?.asOf, locale))
    RateHint.EARLIER_DATE -> stringResource(R.string.rate_hint_earlier)
    RateHint.PLAIN -> stringResource(R.string.income_field_rate_hint)
}

/** "15 Oct, 05:32" in Sri Lanka time, the day the provider counts as today. */
private fun publishedAt(time: Instant?, locale: Locale): String =
    time?.let { DateTimeFormatter.ofPattern("d MMM, HH:mm", locale).withZone(ZarTime.ZONE).format(it) }.orEmpty()
