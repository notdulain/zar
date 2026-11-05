package com.example.zar.scenario

import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.features.capture.domain.model.SmsIgnoreReason
import com.example.zar.features.capture.domain.model.SmsParseResult
import com.example.zar.features.capture.domain.model.SmsReviewFlag
import com.example.zar.features.capture.domain.model.SmsTransactionKind
import com.example.zar.features.capture.domain.parser.BankSmsParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kavindu's bank summary miscategorised a PickMe fare and missed cash (assignment §3.5). Zar reads
 * the bank SMS itself and only proposes a draft. Messages are anonymized test samples.
 */
class KavinduSmsScenarioTest {

    private val parser = BankSmsParser()
    private fun lkr(amount: String) = FiatMoney.parse(amount, Currencies.LKR)!!
    private fun parsed(text: String) = (parser.parse(text) as SmsParseResult.Parsed).sms
    private fun sampath(line: String, date: String) = "$line\n$date\nTo Inq Call 0112303050\nGet protected -Do not Share OTP"

    @Test
    fun aPickMeRideIsReadAsACardPurchaseWithTheRealMerchant() {
        val sms = parsed("Dear Cardholder, Purchase at PICKME COLOMBO 03 LK for LKR 1,250.00 on 20/10/26 08:15 AM has been authorised on your debit card ending #1111.")

        assertEquals(SmsTransactionKind.CARD_PURCHASE, sms.kind)
        assertEquals(Direction.OUT, sms.direction)
        assertEquals(lkr("1250.00"), sms.amount)
        assertEquals(LocalDate.of(2026, 10, 20), sms.date)
        assertEquals("The merchant is kept as written, not turned into 'online retail'", "PICKME COLOMBO 03 LK", sms.counterparty)
    }

    @Test
    fun anAtmWithdrawalIsCapturedSoCashIsNotInvisible() {
        val sms = parsed("Withdrawal at MAIN STREET COLOMBO 11 LK for LKR 6,000.00 on 14/10/26 01:34 PM from card ending #1111.")
        assertEquals(SmsTransactionKind.ATM_WITHDRAWAL, sms.kind)
        assertEquals(lkr("6000.00"), sms.amount)
    }

    @Test
    fun anUberEatsReversalIsNeverIncome() {
        val sms = parsed(sampath("LKR 336.66 credited to AC **3333 for eCom/REV UBER 99999999", "09/10/2026 21:15:47"))
        assertEquals(SmsTransactionKind.REVERSAL, sms.kind)
        assertEquals(Direction.IN, sms.direction)
    }

    @Test
    fun anOtpWithAnAmountIsIgnored() {
        val result = parser.parse("Dear Cardholder, please verify the merchant name and transaction amount before entering the One Time Password (OTP). Keep your OTP confidential and do not share it with anyone. Your OTP at Merchant 'LMT GO' for LKR 500.00 is 000000")
        assertEquals(SmsParseResult.Ignored(SmsIgnoreReason.OTP), result)
    }

    @Test
    fun aDeclinedCardAttemptIsIgnoredBecauseNoMoneyMoved() {
        val result = parser.parse("Dear Cardholder, your card was declined due to insufficient funds. The attempted transaction amount was LKR 599.00 at UBER 852 LK on 12/10/26 12:20 AM. Please check your balance and try again.")
        assertEquals(SmsParseResult.Ignored(SmsIgnoreReason.DECLINED), result)
    }

    @Test
    fun aTransferRequestIsFlaggedForHimToConfirm() {
        val sms = parsed("Fund transfer request WT10000002 P 2026-10-08 at 08:58:26 via Combank Online to A B SILVA for LKR 13500.00 Inquiries+94112353596.")
        assertTrue(SmsReviewFlag.MAY_NOT_BE_COMPLETED in sms.reviewFlags)
    }

    @Test
    fun anUntestedBankIsSentToManualEntryNotGuessed() {
        val result = parser.parse("BOC: Your account has been debited LKR 2,500.00 at KEELLS on 20/10/2026.")
        assertEquals(SmsParseResult.Unrecognised, result)
    }
}
