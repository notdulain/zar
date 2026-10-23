package com.example.zar.features.capture.domain

/** How well Zar reads a bank's messages, from the L01 findings (docs/sms-compatibility.md). */
enum class CompatibilityStatus {
    /** Every tested sample was read correctly. */
    SUPPORTED,

    /** Read, but a field is missing or unclear, so the user confirms it. */
    NEEDS_REVIEW,

    /** No samples tested. Messages go to manual review; never advertised as supported. */
    UNKNOWN,
}

data class BankFormatSupport(
    val bank: String,
    val messages: String,
    val status: CompatibilityStatus,
    val note: String? = null,
)

/** Shown before anyone turns on capture, so untested banks are never presented as supported. */
object BankCompatibility {
    val formats: List<BankFormatSupport> = listOf(
        BankFormatSupport("Commercial Bank", "Card purchases, ATM withdrawals", CompatibilityStatus.SUPPORTED),
        BankFormatSupport("Commercial Bank", "Online fund transfers", CompatibilityStatus.NEEDS_REVIEW, "Time has no AM/PM"),
        BankFormatSupport("Sampath Bank", "Debits and credits", CompatibilityStatus.SUPPORTED, "Credits need you to choose the type"),
        BankFormatSupport("DFCC Bank", "Debits and card fees", CompatibilityStatus.NEEDS_REVIEW, "Messages have no time"),
        BankFormatSupport("Commercial Bank, DFCC", "Money received", CompatibilityStatus.UNKNOWN),
        BankFormatSupport("BOC, People's, HNB, NSB, NDB, Seylan and others", "Any", CompatibilityStatus.UNKNOWN),
    )
}
