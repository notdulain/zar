package com.example.zar.features.capture.domain.model

import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.money.FiatMoney
import java.time.LocalDate
import java.time.LocalTime

/** A bank SMS layout we have tested against real (anonymized) samples. See docs/sms-compatibility.md. */
enum class BankSmsFormat(val bank: String) {
    COMBANK_CARD_PURCHASE("Commercial Bank"),
    COMBANK_ATM_WITHDRAWAL("Commercial Bank"),
    COMBANK_ONLINE_TRANSFER("Commercial Bank"),
    SAMPATH_ACCOUNT_ALERT("Sampath Bank"),
    DFCC_ACCOUNT_ALERT("DFCC Bank"),
}

enum class SmsTransactionKind {
    CARD_PURCHASE,
    ATM_WITHDRAWAL,
    ONLINE_PURCHASE,
    ONLINE_TRANSFER,

    /** A debit the message only describes by a free-text reference or narration. */
    ACCOUNT_DEBIT,

    /** Money in. It may be income, a transfer or a refund: the user decides. */
    ACCOUNT_CREDIT,

    /** An online purchase reversed by the bank. Linked to the original purchase, never income. */
    REVERSAL,
    BANK_FEE,
}

/** Why a parsed message still needs the user's attention before it becomes a record. */
enum class SmsReviewFlag {
    /** The message has no time of day. */
    TIME_MISSING,

    /** The message uses a 12-hour time without AM/PM. */
    TIME_AMBIGUOUS,

    /** The wording ("request") does not confirm the money actually moved. */
    MAY_NOT_BE_COMPLETED,
}

/** Structured fields extracted from one bank SMS. The raw text is never kept here. */
data class ParsedBankSms(
    val format: BankSmsFormat,
    val kind: SmsTransactionKind,
    val direction: Direction,
    /** Always positive. The direction says whether money left or arrived. */
    val amount: FiatMoney,
    val date: LocalDate,
    val time: LocalTime?,
    /** Last digits of the card or account shown in the message, if any. */
    val accountHint: String?,
    /** Merchant, payee or narration exactly as the bank wrote it. */
    val counterparty: String?,
    val reviewFlags: Set<SmsReviewFlag> = emptySet(),
)

enum class SmsIgnoreReason {
    OTP,

    /** A declined card attempt: no money moved. */
    DECLINED,

    /** Notices and promotions with no amount, e.g. maintenance alerts. */
    NOT_A_TRANSACTION,
}

sealed interface SmsParseResult {
    data class Parsed(val sms: ParsedBankSms) : SmsParseResult

    data class Ignored(val reason: SmsIgnoreReason) : SmsParseResult

    /** Mentions money but matches no tested format. Must go to manual review; never guessed. */
    data object Unrecognised : SmsParseResult
}
