package com.example.zar.features.bills.presentation.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.zar.R
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.presentation.theme.ZarLightColors
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillReminder
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Dates, icons and wording shared by the Bills screens (Figma Z22, Z23, Z28). */
internal object BillFormat {
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val WEEKDAY_DAY_MONTH = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val LONG_WEEKDAY_DAY_MONTH = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)

    /** "18 Sep". */
    fun dayMonth(date: LocalDate): String = date.format(DAY_MONTH)

    /** "Fri, 18 Oct". */
    fun weekdayDayMonth(date: LocalDate): String = date.format(WEEKDAY_DAY_MONTH)

    /** "Friday, 18 Oct". */
    fun longDate(date: LocalDate): String = date.format(LONG_WEEKDAY_DAY_MONTH)

    /** "Sunday". */
    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /** "18th", "1st", "22nd". */
    fun ordinal(day: Int): String {
        val suffix = if (day in 11..13) "th" else when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }

    /** "Two" for the hero sentence; larger counts stay as digits. */
    fun countWord(count: Int): String = WORDS.getOrNull(count) ?: count.toString()

    /** "LKR 5,850", or "LKR 5,850 + USD 10" when a total spans currencies. */
    fun totals(amounts: List<Money>): String = amounts.joinToString(" + ") { IncomeFormat.withCode(it) }

    /**
     * Figma gives each bill an icon. Bills don't store one, so it is picked from the name, with a
     * receipt for anything unrecognised.
     */
    @DrawableRes
    fun icon(name: String): Int {
        val n = name.lowercase(Locale.ENGLISH)
        fun has(vararg words: String) = words.any { it in n }
        return when {
            has("internet", "wifi", "wi-fi", "broadband", "fibre", "fiber", "dialog", "slt") -> R.drawable.ic_ph_wifi_high
            has("netflix", "youtube", "spotify", "disney", "prime", "tv", "hbo", "music") -> R.drawable.ic_ph_television
            has("rent", "boarding", "house", "home", "annex", "lease") -> R.drawable.ic_ph_house
            has("gym", "fitness", "yoga", "sport") -> R.drawable.ic_ph_barbell
            has("electric", "ceb", "leco", "power", "water") -> R.drawable.ic_ph_lightning
            has("mobile", "phone", "mobitel", "hutch", "airtel") -> R.drawable.ic_ph_device_mobile
            has("car", "vehicle", "lease", "fuel", "insurance") -> R.drawable.ic_ph_car
            else -> R.drawable.ic_ph_receipt
        }
    }

    fun frequencyRes(frequency: BillFrequency): Int = when (frequency) {
        BillFrequency.WEEKLY -> R.string.bill_weekly
        BillFrequency.MONTHLY -> R.string.bill_monthly
        BillFrequency.YEARLY -> R.string.bill_yearly
    }

    fun everyRes(frequency: BillFrequency): Int = when (frequency) {
        BillFrequency.WEEKLY -> R.string.bill_every_week
        BillFrequency.MONTHLY -> R.string.bill_every_month
        BillFrequency.YEARLY -> R.string.bill_every_year
    }

    fun reminderRes(reminder: BillReminder?): Int = when (reminder) {
        null -> R.string.bill_reminder_off
        BillReminder.ON_THE_DAY -> R.string.bill_reminder_day
        BillReminder.ONE_DAY_BEFORE -> R.string.bill_reminder_1
        BillReminder.THREE_DAYS_BEFORE -> R.string.bill_reminder_3
        BillReminder.ONE_WEEK_BEFORE -> R.string.bill_reminder_7
    }

    /** Sets the words between asterisks in semi-bold, as the Z22 hero does with "bills". */
    fun emphasise(text: String): AnnotatedString = buildAnnotatedString {
        text.split('*').forEachIndexed { index, part ->
            if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
        }
    }

    private val WORDS = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")
}

/** The 6dp solid status dot used in Figma's row meta lines. */
@Composable
internal fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).background(color, CircleShape))
}

/** White status bar glyphs over a dark hero (or in dark theme), dark ones on a light screen. */
@Composable
internal fun StatusBarGlyphs(onDarkHero: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    val light = onDarkHero || ZarTheme.colors.canvas != ZarLightColors.canvas
    DisposableEffect(view, light) {
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        activity?.let { WindowCompat.getInsetsController(it.window, view).isAppearanceLightStatusBars = !light }
        onDispose { }
    }
}
