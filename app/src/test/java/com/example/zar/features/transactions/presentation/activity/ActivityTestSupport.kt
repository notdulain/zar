package com.example.zar.features.transactions.presentation.activity

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.ConnectivityMonitor
import com.example.zar.core.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

internal class FakeAccounts(initial: List<Account>) : AccountRepository {
    val accounts = MutableStateFlow(initial)
    override fun observeAccounts(): Flow<List<Account>> = accounts
    override suspend fun saveAccount(account: Account) = Unit
}

internal class FakeConnectivity(initial: Boolean = true) : ConnectivityMonitor {
    val online = MutableStateFlow(initial)
    override fun observeOnline(): Flow<Boolean> = online
}

/**
 * The shared in-memory ledger with switches for the failures the screens must survive: a load that
 * fails the first [loadFailures] times, a void that fails, and a correction that is stored and then
 * reported as failed (a lost acknowledgement).
 */
internal class FlakyLedger(val inner: FakeLedgerRepository = FakeLedgerRepository()) : LedgerRepository by inner {
    var loadFailures = 0
    var voidError: Throwable? = null
    var correctionError: Throwable? = null
    var correctionStoredBeforeError = false
    var correctionCalls = 0

    override fun observeLedger(): Flow<List<LedgerEntry>> = flow {
        if (loadFailures > 0) {
            loadFailures--
            throw IllegalStateException("load failed")
        }
        inner.observeLedger().collect { emit(it) }
    }

    override suspend fun void(markers: List<VoidMarker>) {
        voidError?.let { throw it }
        inner.void(markers)
    }

    override suspend fun addCorrection(correction: Correction) {
        correctionCalls++
        val error = correctionError
        if (error != null && !correctionStoredBeforeError) throw error
        inner.addCorrection(correction)
        if (error != null) throw error
    }

    suspend fun save(vararg transactions: Transaction) = transactions.forEach { inner.saveTransaction(it) }
}
