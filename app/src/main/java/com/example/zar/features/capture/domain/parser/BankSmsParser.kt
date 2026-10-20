package com.example.zar.features.capture.domain.parser

import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.money.AssetKind
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
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import javax.inject.Inject

/**
 * Extracts structured fields from Sri Lankan bank SMS bodies. Only layouts verified against real
 * samples are parsed (docs/sms-compatibility.md); anything else that mentions money is
 * [SmsParseResult.Unrecognised] and goes to manual review. Works on the body alone, because
 * shared text does not carry the sender ID.
 */
class BankSmsParser @Inject constructor() {

    private val formats: List<(String) -> ParsedBankSms?> = listOf(
        ::parseCombankPurchase,
        ::parseCombankWithdrawal,
        ::parseCombankTransfer,
        ::parseSampathAlert,
        ::parseDfccAlert,
    )

    fun parse(text: String): SmsParseResult {
        val body = text.replace(WHITESPACE, " ").trim()
        if (OTP.containsMatchIn(body)) return SmsParseResult.Ignored(SmsIgnoreReason.OTP)
        if (DECLINED.containsMatchIn(body)) return SmsParseResult.Ignored(SmsIgnoreReason.DECLINED)
        formats.firstNotNullOfOrNull { it(body) }?.let { return SmsParseResult.Parsed(it) }
        return if (ANY_AMOUNT.containsMatchIn(body)) {
            SmsParseResult.Unrecognised
        } else {
            SmsParseResult.Ignored(SmsIgnoreReason.NOT_A_TRANSACTION)
        }
    }

    private fun parseCombankPurchase(body: String): ParsedBankSms? {
        val (merchant, code, amount, date, time, card) = COMBANK_PURCHASE.matchEntire(body)?.destructured ?: return null
        return ParsedBankSms(
            format = BankSmsFormat.COMBANK_CARD_PURCHASE,
            kind = SmsTransactionKind.CARD_PURCHASE,
            direction = Direction.OUT,
            amount = money(code, amount) ?: return null,
            date = date.toDate(COMBANK_DATE) ?: return null,
            time = time.toTime(COMBANK_TIME) ?: return null,
            accountHint = card,
            counterparty = merchant,
        )
    }

    private fun parseCombankWithdrawal(body: String): ParsedBankSms? {
        val (location, code, amount, date, time, card) = COMBANK_WITHDRAWAL.matchEntire(body)?.destructured ?: return null
        return ParsedBankSms(
            format = BankSmsFormat.COMBANK_ATM_WITHDRAWAL,
            kind = SmsTransactionKind.ATM_WITHDRAWAL,
            direction = Direction.OUT,
            amount = money(code, amount) ?: return null,
            date = date.toDate(COMBANK_DATE) ?: return null,
            time = time.toTime(COMBANK_TIME) ?: return null,
            accountHint = card,
            counterparty = location,
        )
    }

    /** The time is 12-hour without AM/PM (a 21:59 message said 09:59:34), so it is dropped. */
    private fun parseCombankTransfer(body: String): ParsedBankSms? {
        val (date, payee, code, amount) = COMBANK_TRANSFER.matchEntire(body)?.destructured ?: return null
        return ParsedBankSms(
            format = BankSmsFormat.COMBANK_ONLINE_TRANSFER,
            kind = SmsTransactionKind.ONLINE_TRANSFER,
            direction = Direction.OUT,
            amount = money(code, amount) ?: return null,
            date = date.toDate(DateTimeFormatter.ISO_LOCAL_DATE) ?: return null,
            time = null,
            accountHint = null,
            counterparty = payee,
            reviewFlags = setOf(SmsReviewFlag.TIME_AMBIGUOUS, SmsReviewFlag.MAY_NOT_BE_COMPLETED),
        )
    }

    private fun parseSampathAlert(body: String): ParsedBankSms? {
        val (code, amount, verb, account, channel, counterparty, date, time) =
            SAMPATH_ALERT.matchEntire(body)?.destructured ?: return null
        val direction = if (verb == "debited from") Direction.OUT else Direction.IN
        val kind = when (channel to direction) {
            "via POS at" to Direction.OUT -> SmsTransactionKind.CARD_PURCHASE
            "via ATM at" to Direction.OUT -> SmsTransactionKind.ATM_WITHDRAWAL
            "for eCom" to Direction.OUT -> SmsTransactionKind.ONLINE_PURCHASE
            "for eCom/REV" to Direction.IN -> SmsTransactionKind.REVERSAL
            "for" to Direction.OUT -> SmsTransactionKind.ACCOUNT_DEBIT
            "for" to Direction.IN -> SmsTransactionKind.ACCOUNT_CREDIT
            else -> return null
        }
        return ParsedBankSms(
            format = BankSmsFormat.SAMPATH_ACCOUNT_ALERT,
            kind = kind,
            direction = direction,
            amount = money(code, amount) ?: return null,
            date = date.toDate(SAMPATH_DATE) ?: return null,
            time = time.toTime(DateTimeFormatter.ISO_LOCAL_TIME) ?: return null,
            accountHint = account,
            counterparty = counterparty,
        )
    }

    /** Only debits are parsed: no DFCC credit sample has been collected yet. */
    private fun parseDfccAlert(body: String): ParsedBankSms? {
        val (account, code, amount, date, ref) = DFCC_ALERT.matchEntire(body)?.destructured ?: return null
        return ParsedBankSms(
            format = BankSmsFormat.DFCC_ACCOUNT_ALERT,
            kind = if (ref.contains("Transaction Fee", ignoreCase = true)) {
                SmsTransactionKind.BANK_FEE
            } else {
                SmsTransactionKind.ACCOUNT_DEBIT
            },
            direction = Direction.OUT,
            amount = money(code, amount) ?: return null,
            date = date.toDate(DFCC_DATE) ?: return null,
            time = null,
            accountHint = account,
            counterparty = ref,
            reviewFlags = setOf(SmsReviewFlag.TIME_MISSING),
        )
    }

    private fun money(code: String, amount: String): FiatMoney? {
        val currency = Currencies.find(code)?.takeIf { it.kind == AssetKind.FIAT } ?: return null
        return FiatMoney.parse(amount, currency)?.takeIf { it.isPositive }
    }

    private fun String.toDate(formatter: DateTimeFormatter): LocalDate? =
        runCatching { LocalDate.parse(this, formatter) }.getOrNull()

    private fun String.toTime(formatter: DateTimeFormatter): LocalTime? =
        runCatching { LocalTime.parse(this, formatter) }.getOrNull()

    private companion object {
        const val AMOUNT = """\d[\d,]*(?:\.\d{1,2})?"""

        val WHITESPACE = Regex("""\s+""")
        val OTP = Regex("""One Time Password|\bOTP\b.{0,80}\bis \d{4,8}\b""", RegexOption.IGNORE_CASE)
        val DECLINED = Regex("""\bdeclined\b""", RegexOption.IGNORE_CASE)
        val ANY_AMOUNT = Regex("""\b(?:LKR|Rs|USD|EUR|GBP|AUD|INR)\.? ?\d""", RegexOption.IGNORE_CASE)

        val COMBANK_PURCHASE = Regex(
            """Dear Cardholder, Purchase at (.+?) for ([A-Z]{3}) ($AMOUNT) on (\d{2}/\d{2}/\d{2}) """ +
                """(\d{1,2}:\d{2} [AP]M) has been authorised on your (?:debit|credit) card ending #(\d{4})\.?""",
        )
        val COMBANK_WITHDRAWAL = Regex(
            """Withdrawal at (.+?) for ([A-Z]{3}) ($AMOUNT) on (\d{2}/\d{2}/\d{2}) (\d{1,2}:\d{2} [AP]M) """ +
                """from card ending #(\d{4})\.?""",
        )
        val COMBANK_TRANSFER = Regex(
            """Fund transfer request \S+ P (\d{4}-\d{2}-\d{2}) at \d{2}:\d{2}:\d{2} via Combank Online """ +
                """to (.+?) for ([A-Z]{3}) ($AMOUNT) Inquiries.*""",
        )
        val SAMPATH_ALERT = Regex(
            """([A-Z]{3}) ($AMOUNT) (debited from|credited to) AC \*+(\d{3,4}) """ +
                """(via POS at|via ATM at|for eCom/REV|for eCom|for) (.+?) """ +
                """(\d{2}/\d{2}/\d{4}) (\d{2}:\d{2}:\d{2})(?: To Inq Call.*)?""",
        )
        val DFCC_ALERT = Regex(
            """Your A/C No: \*+(\d{3,4}) is debited with ([A-Z]{3}) ?($AMOUNT) on (\d{1,2} [A-Za-z]{3} \d{4}) """ +
                """ref: (.+?)\. Your bal is .*""",
        )

        val COMBANK_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
        val SAMPATH_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val COMBANK_TIME: DateTimeFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("h:mm a")
            .toFormatter(Locale.ENGLISH)
        val DFCC_DATE: DateTimeFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM yyyy")
            .toFormatter(Locale.ENGLISH)
    }
}
