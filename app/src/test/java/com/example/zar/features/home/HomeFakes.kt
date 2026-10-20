package com.example.zar.features.home

import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Ledger whose entries a test sets directly; only reading is supported. */
class StaticLedgerRepository(initial: List<LedgerEntry> = emptyList()) : LedgerRepository {
    val entries = MutableStateFlow(initial)

    /** Replace to simulate a failing read, e.g. `flow { throw IOException() }`. */
    var source: Flow<List<LedgerEntry>>? = null

    override fun observeLedger(): Flow<List<LedgerEntry>> = source ?: entries

    override suspend fun saveTransaction(transaction: Transaction) = error("not used")
    override suspend fun saveTransfer(transfer: Transfer) = error("not used")
    override suspend fun addCorrection(correction: Correction) = error("not used")
    override suspend fun void(markers: List<VoidMarker>) = error("not used")
    override suspend fun linkEvidence(link: EvidenceLink) = error("not used")
}

internal fun emptyLedger(): Flow<List<LedgerEntry>> = flowOf(emptyList())
