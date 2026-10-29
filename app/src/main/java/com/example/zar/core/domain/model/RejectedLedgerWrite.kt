package com.example.zar.core.domain.model

/**
 * A save the phone accepted locally and the server then refused. Firestore rolls such a save back, so the
 * records are kept here; trying again writes them under the same IDs and cannot duplicate them.
 *
 * [id] is the transfer ID for a transfer and the transaction ID otherwise. [transactions] holds every record
 * of the save (both transfer legs and any fee).
 */
data class RejectedLedgerWrite(
    val id: String,
    val transactions: List<Transaction>,
)
