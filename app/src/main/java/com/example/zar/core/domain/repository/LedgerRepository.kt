package com.example.zar.core.domain.repository

import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.RejectedLedgerWrite
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.VoidMarker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Actual money movements for the signed-in user.
 *
 * Saving rules every implementation must keep:
 * - Every record is write-once and keyed by its stable ID. Saving the same ID again is a no-op,
 *   so retries and offline replays cannot duplicate, overwrite or revive anything.
 * - Edits are [Correction]s; undo is a [VoidMarker]. Originals are never modified or deleted.
 * - Save functions return once the write is stored locally (they work offline); sync happens later.
 * - A save the server later refuses is rolled back locally. It must not vanish silently: it is reported
 *   through [observeRejectedWrites] with its records so it can be tried again under the same IDs.
 * - All calls throw LedgerError.NotSignedIn when nobody is signed in.
 */
interface LedgerRepository {
    /** Every entry, including voided ones (use `active()` to drop them), newest first. */
    fun observeLedger(): Flow<List<LedgerEntry>>

    suspend fun saveTransaction(transaction: Transaction)

    /** Saves both legs and any separate fee expense in one atomic write. */
    suspend fun saveTransfer(transfer: Transfer)

    suspend fun addCorrection(correction: Correction)

    /** Writes all markers atomically (e.g. both transfer legs). */
    suspend fun void(markers: List<VoidMarker>)

    suspend fun linkEvidence(link: EvidenceLink)

    /** Saves the server refused after the phone accepted them, oldest first. Empty when nothing was refused. */
    fun observeRejectedWrites(): Flow<List<RejectedLedgerWrite>> = flowOf(emptyList())
}
