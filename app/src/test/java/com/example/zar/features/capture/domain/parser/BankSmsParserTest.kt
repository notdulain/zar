package com.example.zar.features.capture.domain.parser

import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.features.capture.domain.model.BankSmsFormat
import com.example.zar.features.capture.domain.model.ParsedBankSms
import com.example.zar.features.capture.domain.model.SmsIgnoreReason
import com.example.zar.features.capture.domain.model.SmsParseResult
import com.example.zar.features.capture.domain.model.SmsReviewFlag
import com.example.zar.features.capture.domain.model.SmsTransactionKind
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Real messages collected for L01 (October 2026), anonymized: card and account digits, people's
 * names, references, OTP codes and balances are replaced. Layout, spacing and line breaks are kept.
 */
class BankSmsParserTest {

    private val parser = BankSmsParser()

    private fun lkr(amount: String) = FiatMoney.parse(amount, Currencies.LKR)!!

    private fun assertParsed(expected: ParsedBankSms, text: String) {
        assertEquals(SmsParseResult.Parsed(expected), parser.parse(text))
    }

    // Commercial Bank -----------------------------------------------------------------------

    @Test
    fun combankCardPurchases() {
        val samples = listOf(
            Triple(
                "Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111.",
                Triple("CAFE SEVENTY THREE MALABE LK", "800.00", "1111"),
                LocalDate.of(2026, 10, 11) to LocalTime.of(10, 49),
            ),
            Triple(
                "Dear Cardholder, Purchase at MINTPAY PVT LTD COLOMBO 06 LK for LKR 1,094.95 on 09/10/26 06:31 PM has been authorised on your debit card ending #1111.",
                Triple("MINTPAY PVT LTD COLOMBO 06 LK", "1094.95", "1111"),
                LocalDate.of(2026, 10, 9) to LocalTime.of(18, 31),
            ),
            Triple(
                "Dear Cardholder, Purchase at LUMBINI ENTERPRISES MALABE LK for LKR 1,000.00 on 08/10/26 07:36 AM has been authorised on your debit card ending #2222.",
                Triple("LUMBINI ENTERPRISES MALABE LK", "1000.00", "2222"),
                LocalDate.of(2026, 10, 8) to LocalTime.of(7, 36),
            ),
            Triple(
                "Dear Cardholder, Purchase at NE - 6578 EKALA LK for LKR 210.00 on 09/10/26 07:07 PM has been authorised on your debit card ending #2222.",
                Triple("NE - 6578 EKALA LK", "210.00", "2222"),
                LocalDate.of(2026, 10, 9) to LocalTime.of(19, 7),
            ),
            Triple(
                "Dear Cardholder, Purchase at KEELLS KADUWELA KADUWELA COLK for LKR 555.00 on 11/10/26 12:14 PM has been authorised on your debit card ending #2222.",
                Triple("KEELLS KADUWELA KADUWELA COLK", "555.00", "2222"),
                LocalDate.of(2026, 10, 11) to LocalTime.of(12, 14),
            ),
        )
        samples.forEach { (text, fields, at) ->
            assertParsed(
                ParsedBankSms(
                    format = BankSmsFormat.COMBANK_CARD_PURCHASE,
                    kind = SmsTransactionKind.CARD_PURCHASE,
                    direction = Direction.OUT,
                    amount = lkr(fields.second),
                    date = at.first,
                    time = at.second,
                    accountHint = fields.third,
                    counterparty = fields.first,
                ),
                text,
            )
        }
    }

    @Test
    fun combankAtmWithdrawal() {
        assertParsed(
            ParsedBankSms(
                format = BankSmsFormat.COMBANK_ATM_WITHDRAWAL,
                kind = SmsTransactionKind.ATM_WITHDRAWAL,
                direction = Direction.OUT,
                amount = lkr("6000.00"),
                date = LocalDate.of(2026, 10, 14),
                time = LocalTime.of(13, 34),
                accountHint = "1111",
                counterparty = "MAIN STREET COLOMBO 11 LK",
            ),
            "Withdrawal at MAIN STREET\nCOLOMBO 11 LK for LKR\n6,000.00 on 14/10/26 01:34 PM\nfrom card ending #1111.",
        )
    }

    @Test
    fun combankOnlineTransferDropsTheAmbiguousTime() {
        assertParsed(
            ParsedBankSms(
                format = BankSmsFormat.COMBANK_ONLINE_TRANSFER,
                kind = SmsTransactionKind.ONLINE_TRANSFER,
                direction = Direction.OUT,
                amount = lkr("200.00"),
                date = LocalDate.of(2026, 10, 6),
                time = null,
                accountHint = null,
                counterparty = "JOHN DOE",
                reviewFlags = setOf(SmsReviewFlag.TIME_AMBIGUOUS, SmsReviewFlag.MAY_NOT_BE_COMPLETED),
            ),
            "Fund transfer request\nWT10000001 P 2026-10-06 at\n09:59:34 via Combank Online to\nJOHN DOE for LKR 200.00\nInquiries+94112353596.",
        )
    }

    @Test
    fun combankTransferWithoutThousandsSeparator() {
        val result = parser.parse(
            "Fund transfer request WT10000002 P 2026-10-08 at 08:58:26 via Combank Online to A B SILVA for LKR 13500.00 Inquiries+94112353596.",
        )
        assertEquals(lkr("13500.00"), (result as SmsParseResult.Parsed).sms.amount)
    }

    // Sampath Bank --------------------------------------------------------------------------

    private fun sampath(line: String, date: String) =
        "$line\n$date\nTo Inq Call 0112303050\nGet protected -Do not Share OTP"

    @Test
    fun sampathAlertsCoverEveryChannel() {
        data class Case(val text: String, val kind: SmsTransactionKind, val amount: String, val counterparty: String)

        val cases = listOf(
            Case(
                sampath("LKR 650.00 debited from AC **3333 via POS at GOLDEN MEAL RESTAURANT 80500323", "09/10/2026 21:15:47"),
                SmsTransactionKind.CARD_PURCHASE, "650.00", "GOLDEN MEAL RESTAURANT 80500323",
            ),
            Case(
                sampath("LKR 2,630.00 debited from AC **3333 via ATM at KOTHALAWALA 1 SR CASH SET", "09/10/2026 21:15:47"),
                SmsTransactionKind.ATM_WITHDRAWAL, "2630.00", "KOTHALAWALA 1 SR CASH SET",
            ),
            Case(
                sampath("LKR 1,598.00 debited from AC **3333 for eCom MOBITEL (PVT) LTD 90900661", "09/10/2026 21:15:47"),
                SmsTransactionKind.ONLINE_PURCHASE, "1598.00", "MOBITEL (PVT) LTD 90900661",
            ),
            Case(
                sampath("LKR 336.66 credited to AC **3333 for eCom/REV UBER 99999999", "09/10/2026 21:15:47"),
                SmsTransactionKind.REVERSAL, "336.66", "UBER 99999999",
            ),
            Case(
                sampath("LKR 1,525.00 debited from AC **3333 for friend rice", "09/10/2026 21:15:47"),
                SmsTransactionKind.ACCOUNT_DEBIT, "1525.00", "friend rice",
            ),
            Case(
                sampath("LKR 399.00 debited from AC **3333 for VSRPMT-000000000001", "09/10/2026 21:15:47"),
                SmsTransactionKind.ACCOUNT_DEBIT, "399.00", "VSRPMT-000000000001",
            ),
            Case(
                sampath("LKR 240.00 credited to AC **3333 for Food", "09/10/2026 21:15:47"),
                SmsTransactionKind.ACCOUNT_CREDIT, "240.00", "Food",
            ),
        )
        cases.forEach { case ->
            val sms = (parser.parse(case.text) as SmsParseResult.Parsed).sms
            assertEquals(case.text, case.kind, sms.kind)
            assertEquals(case.text, lkr(case.amount), sms.amount)
            assertEquals(case.text, case.counterparty, sms.counterparty)
            assertEquals(BankSmsFormat.SAMPATH_ACCOUNT_ALERT, sms.format)
            assertEquals("3333", sms.accountHint)
            assertEquals(LocalDate.of(2026, 10, 9), sms.date)
            assertEquals(LocalTime.of(21, 15, 47), sms.time)
            assertEquals(emptySet<SmsReviewFlag>(), sms.reviewFlags)
        }
    }

    @Test
    fun sampathCreditIsMoneyIn() {
        val sms = (parser.parse(sampath("LKR 200.00 credited to AC **3333 for wifi", "06/10/2026 21:57:27")) as SmsParseResult.Parsed).sms
        assertEquals(Direction.IN, sms.direction)
        assertEquals(LocalDate.of(2026, 10, 6), sms.date)
    }

    // DFCC Bank -----------------------------------------------------------------------------

    @Test
    fun dfccDebitHasNoTime() {
        assertParsed(
            ParsedBankSms(
                format = BankSmsFormat.DFCC_ACCOUNT_ALERT,
                kind = SmsTransactionKind.ACCOUNT_DEBIT,
                direction = Direction.OUT,
                amount = lkr("2643.69"),
                date = LocalDate.of(2026, 10, 9),
                time = null,
                accountHint = "4444",
                counterparty = "Netflix.com Los Gatos",
                reviewFlags = setOf(SmsReviewFlag.TIME_MISSING),
            ),
            "Your A/C No: ********4444 is debited with LKR2,643.69 on 09 OCT 2026 ref: Netflix.com Los Gatos. Your bal is LKR10,000.00. If unauthorized call 0112350035",
        )
    }

    @Test
    fun dfccPosFeeIsABankFee() {
        val sms = (
            parser.parse(
                "Your A/C No: ********4444 is debited with LKR92.53 on 09 OCT 2026 ref: POS Transaction Fee. Your bal is LKR10,000.00. If unauthorized call 0112350035",
            ) as SmsParseResult.Parsed
            ).sms
        assertEquals(SmsTransactionKind.BANK_FEE, sms.kind)
        assertEquals(lkr("92.53"), sms.amount)
    }

    @Test
    fun dfccMerchantWithParenthesis() {
        val sms = (
            parser.parse(
                "Your A/C No: ********4444 is debited with LKR150.00 on 11 OCT 2026 ref: PERERA SONS BAKERS (PVT MALAMBE. Your bal is LKR10,000.00. If unauthorized call 0112350035",
            ) as SmsParseResult.Parsed
            ).sms
        assertEquals("PERERA SONS BAKERS (PVT MALAMBE", sms.counterparty)
    }

    // Messages that must never become transactions ------------------------------------------

    @Test
    fun declinedCardAttemptsAreIgnored() {
        assertEquals(
            SmsParseResult.Ignored(SmsIgnoreReason.DECLINED),
            parser.parse(
                "Dear Cardholder, your card was declined due to insufficient funds. The attempted transaction amount was LKR 599.00 at UBER 852 LK on 12/10/26 12:20 AM. Please check your balance and try again.",
            ),
        )
    }

    @Test
    fun otpMessagesAreIgnored() {
        listOf(
            "<#> Forwarded from COMBANK : Do NOT share this number with anyone. Code 000000 is your One Time Password to authorize the WT10000001 transaction AbCdEfG1234",
            "Dear Cardholder, please verify the merchant name and transaction amount before entering the One Time Password (OTP). Keep your OTP confidential and do not share it with anyone. Your OTP at Merchant 'LMT GO' for LKR 500.00 is 000000",
        ).forEach { assertEquals(it, SmsParseResult.Ignored(SmsIgnoreReason.OTP), parser.parse(it)) }
    }

    @Test
    fun noticesWithoutAmountsAreIgnored() {
        assertEquals(
            SmsParseResult.Ignored(SmsIgnoreReason.NOT_A_TRANSACTION),
            parser.parse(
                "Dear Customer, due to scheduled maintenance on 27 Sep from 2:00 AM to 3:00 AM SL time, Debit Card transactions (online & physical), ATMs/CRMs, ComBank Digital (App & Web), Q+ App & all other digital services will be temporarily unavailable. We apologise for the inconvenience.",
            ),
        )
    }

    @Test
    fun unknownMessagesWithMoneyGoToReview() {
        listOf(
            // A bank or wording we have not tested.
            "Your account 1234 has been credited with LKR 5,000.00 on 10/10/2026.",
            // DFCC credits: no real sample collected yet, so not claimed as supported.
            "Your A/C No: ********4444 is credited with LKR5,000.00 on 09 OCT 2026 ref: SALARY. Your bal is LKR10,000.00. If unauthorized call 0112350035",
        ).forEach { assertEquals(it, SmsParseResult.Unrecognised, parser.parse(it)) }
    }
}
