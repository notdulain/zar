package com.example.zar.core.presentation.privacy

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.format.formatForRow
import com.example.zar.core.presentation.format.formatNumber
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AmountMaskTest {

    private val lkr = FiatMoney(14_835_000, Currencies.LKR)
    private val usd = FiatMoney(12_345, Currencies.USD)

    @Before fun show() = AmountMask.set(false)
    @After fun reset() = AmountMask.set(false)

    @Test
    fun amountsAreShownUnlessTheUserHidesThem() {
        assertFalse(AmountMask.isHidden)
        assertEquals("LKR 148,350", lkr.formatWithCode())
        assertEquals("148,350", lkr.formatForRow())
        assertEquals("USD 123.45", usd.formatWithCode())
    }

    @Test
    fun everyFormatterMasksTheNumberWhenHidden() {
        AmountMask.set(true)
        assertEquals("••••", lkr.formatNumber())
        assertEquals("LKR ••••", lkr.formatWithCode())
        assertEquals("••••", lkr.formatForRow())
        assertEquals("USD ••••", usd.formatForRow())
        assertEquals("••••", IncomeFormat.amount(lkr))
        assertEquals("USD ••••", IncomeFormat.withCode(usd))
        assertEquals("+ ••••", IncomeFormat.received(lkr))
        assertEquals("+ USD ••••", IncomeFormat.received(usd))
    }

    @Test
    fun noDigitOfTheAmountSurvivesMasking() {
        AmountMask.set(true)
        val shown = listOf(lkr.formatWithCode(), usd.formatWithCode(), IncomeFormat.received(lkr))
        shown.forEach { text -> assertFalse("leaked in $text", Regex("[1-9]").containsMatchIn(text)) }
    }

    @Test
    fun zeroAndCryptoAreMaskedToo() {
        AmountMask.set(true)
        assertEquals("LKR ••••", FiatMoney(0, Currencies.LKR).formatWithCode())
        assertEquals("BTC ••••", CryptoAmount.of("0.00012345".toBigDecimal(), Currencies.BTC).formatWithCode())
    }

    @Test
    fun unhidingRestoresTheExactValues() {
        val before = listOf(lkr.formatWithCode(), usd.formatForRow(), IncomeFormat.received(lkr))
        AmountMask.set(true)
        AmountMask.set(false)
        assertEquals(before, listOf(lkr.formatWithCode(), usd.formatForRow(), IncomeFormat.received(lkr)))
    }

    @Test
    fun theCurrencyCodeStaysSoAnAmountIsNeverAmbiguous() {
        AmountMask.set(true)
        assertTrue(usd.formatWithCode().startsWith("USD"))
    }

    @Test
    fun amountFieldsAreMaskedCharacterForCharacterWhileHidden() {
        val typed = AnnotatedString("1500.50")
        assertEquals("1500.50", amountFieldTransformation().filter(typed).text.text)

        AmountMask.set(true)
        val masked = amountFieldTransformation().filter(typed).text.text
        assertEquals(typed.length, masked.length)
        assertTrue(masked.all { it == '•' })
        assertFalse(amountFieldTransformation() === VisualTransformation.None)
    }
}
