package com.example.zar.features.quicknote.data

import com.example.zar.features.quicknote.data.mapper.AmountGrounding
import java.math.BigDecimal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountGroundingTest {
    private fun grounded(amount: String, note: String) = AmountGrounding.isGrounded(BigDecimal(amount), note)

    @Test fun plainNumbers() {
        assertTrue(grounded("2450", "dialog bill eka 2450 gewwa card eken"))
        assertTrue(grounded("850", "lunch 850 at keells"))
        assertFalse(grounded("2540", "dialog bill eka 2450 gewwa"))
    }

    @Test fun thousandsSeparatorsAndDecimals() {
        assertTrue(grounded("1250.50", "paid Rs 1,250.50 for fuel"))
        assertTrue(grounded("245000", "salary 2,45,000"))
        assertFalse(grounded("1250", "Rs. 12500."))
    }

    @Test fun magnitudes() {
        assertTrue(grounded("5000", "got 5k from nimal"))
        assertTrue(grounded("2500", "around 2.5k"))
        assertTrue(grounded("150000", "1.5 lakh advance"))
        assertTrue(grounded("200000", "2 lakhs"))
        assertTrue(grounded("2000000", "2mn project"))
        assertTrue(grounded("3000", "3 thousand"))
        assertFalse("5k is not 5", grounded("5", "got 5k"))
        assertFalse("k scales only the number it follows", grounded("3000", "2k and 3"))
    }

    @Test fun rangesAreNotGroundedToTheirMidpoint() {
        assertFalse(grounded("2500", "2-3k for food"))
        assertTrue(grounded("3000", "2-3k for food"))
    }

    @Test fun nonLatinNumerals() {
        assertTrue("Sinhala digits", grounded("2450", "බිල ෨෪෫෦ ක්"))
        assertTrue("Tamil digits", grounded("1500", "௧௫௦௦ ரூ"))
        assertTrue("Arabic-Indic digits", grounded("750", "٧٥٠"))
        assertTrue("full-width digits", grounded("850", "lunch ８５０"))
        assertTrue("Tamil digits with k", grounded("5000", "௫k"))
    }

    @Test fun nonMagnitudeWordsDoNotScale() {
        assertTrue(grounded("50", "bought 50 usdt"))
        assertFalse(grounded("50000", "bought 50 usdt"))
        assertTrue(grounded("2", "2 kg rice"))
    }

    @Test fun missingNumberIsNotGrounded() {
        assertFalse(grounded("100", "paid the bill yesterday"))
    }
}
