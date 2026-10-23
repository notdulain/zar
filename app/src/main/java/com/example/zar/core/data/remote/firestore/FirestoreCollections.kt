package com.example.zar.core.data.remote.firestore

/**
 * Firestore is the primary financial store. Every document lives under users/{uid}/..., and
 * firestore.rules only lets that user read or write it. Encrypted Room staging is used only for
 * captured messages awaiting review, never for financial records.
 */
object FirestoreCollections {
    const val USERS = "users"
    const val ACCOUNTS = "accounts"
    const val TRANSACTIONS = "transactions"
    const val CORRECTIONS = "corrections"
    const val VOIDS = "voids"
    const val EVIDENCE_LINKS = "evidenceLinks"
    const val EXPECTED_PAYMENTS = "expectedPayments"
    const val PAYMENT_MATCHES = "paymentMatches"
    const val DRAFTS = "drafts"
    const val PROJECTS = "projects"
    const val CRYPTO_OPENING_COSTS = "cryptoOpeningCosts"
    const val BILLS = "bills"
    const val GOALS = "goals"
    const val ALLOWANCE = "allowance"
}
