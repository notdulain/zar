package com.example.zar.features.rates.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RateTextTest {

    private fun parsed(text: String): BigDecimal? = RateText.parse(text)

    private fun assertRate(expected: String, text: String) {
        val actual = parsed(text)
        assertEquals("'$text' should parse", true, actual != null)
        assertEquals("'$text'", 0, BigDecimal(expected).compareTo(actual))
    }

    @Test
    fun aPlainDecimalWithADotIsReadAsTyped() {
        assertRate("300.5", "300.5")
        assertRate("300.50", "300.50")
        assertRate("332", "332")
        assertRate("0.0033", "0.0033")
    }

    @Test
    fun aSingleCommaBeforeOneOrTwoDigitsIsADecimalSeparator() {
        // Phones set to a comma-decimal language type a comma from the decimal key. It used to be dropped, so 300,5 became 3005.
        assertRate("300.5", "300,5")
        assertRate("300.50", "300,50")
        assertRate("0.5", "0,5")
    }

    @Test
    fun commasWithADotAreThousandsSeparators() {
        assertRate("1100.5", "1,100.5")
        assertRate("1234567.25", "1,234,567.25")
    }

    @Test
    fun commasInGroupsOfThreeDigitsAreThousandsSeparators() {
        // A rate near 1.1 LKR per unit does not exist, so 1,100 means one thousand one hundred.
        assertRate("1100", "1,100")
        assertRate("1234567", "1,234,567")
    }

    @Test
    fun surroundingSpacesAreIgnored() {
        assertRate("300.5", "  300,5 ")
    }

    @Test
    fun anythingElseIsRejectedInsteadOfGuessed() {
        assertNull(parsed(""))
        assertNull(parsed("   "))
        assertNull(parsed("abc"))
        assertNull(parsed("0"))
        assertNull(parsed("0,0"))
        assertNull(parsed("-5"))
        assertNull(parsed("1,2,3"))
        assertNull(parsed("300,5,5"))
        assertNull(parsed("12,3456"))
        assertNull(parsed("300.5.5"))
        // A decimal comma after a dot grouping: reading it as thousands would be 1.1005, a thousand times too low.
        assertNull(parsed("1.100,50"))
    }
}
