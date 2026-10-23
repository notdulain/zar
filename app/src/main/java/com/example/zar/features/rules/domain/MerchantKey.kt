package com.example.zar.features.rules.domain

import java.util.Locale

/**
 * The precise identifier a rule matches on. Two names are the same only when they are equal after
 * trimming, collapsing spaces and ignoring case: "Keells" never matches "Keells Super".
 */
object MerchantKey {
    /** Fewer letters or digits than this is not precise enough to remember. */
    const val MIN_LENGTH = 3

    /**
     * Names that cover many different purchases, so one category would often be wrong. A name is
     * general only when it is exactly one of these ("PickMe" is general, "PickMe Food" is not).
     */
    private val GENERIC = setOf(
        "pickme", "uber", "bolt", "grab", "taxi", "cab", "payment", "purchase", "pos", "card", "online",
        "atm", "transfer", "withdrawal", "unknown", "shop", "store", "supermarket", "food", "cash",
    )

    private val spaces = Regex("""\s+""")

    /** Null when [name] is blank or too short to be a precise identifier. */
    fun of(name: String?): String? {
        val tidy = name.orEmpty().lowercase(Locale.ROOT).replace(spaces, " ").trim().trim { !it.isLetterOrDigit() }
        return tidy.takeIf { it.count(Char::isLetterOrDigit) >= MIN_LENGTH }
    }

    fun isGeneric(key: String): Boolean = key in GENERIC
}
