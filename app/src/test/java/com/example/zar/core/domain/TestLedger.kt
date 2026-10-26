package com.example.zar.core.domain

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.RejectedLedgerWrite
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ExchangeRate
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.RateSource
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.core.domain.repository.LedgerRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Shared fixtures for domain tests. */
object TestLedger {
    val T0: Instant = Instant.parse("2026-10-01T00:00:00Z")
    val OCT_10: LocalDate = LocalDate.of(2026, 10, 10)

    fun lkr(major: Long) = FiatMoney(major * 100, Currencies.LKR)
    fun usd(major: Long) = FiatMoney(major * 100, Currencies.USD)

    val usdRate = ExchangeRate(Currencies.USD, Currencies.LKR, "300".toBigDecimal(), T0, RateSource.MANUAL)

    val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(lkr(10_000)), LocalDate.of(2026, 10, 1))
    val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Known(lkr(2_000)), LocalDate.of(2026, 10, 1))
    val payoneer = Account(AccountId("payoneer"), "Payoneer", AccountType.FREELANCE, Currencies.USD, OpeningBalance.Known(usd(0)), LocalDate.of(2026, 10, 1))

    fun tx(
        id: String,
        type: TransactionType,
        amount: Money = lkr(1_000),
        account: Account = bank,
        date: LocalDate = OCT_10,
        spendingClass: SpendingClass? = if (type == TransactionType.EXPENSE) SpendingClass.DISCRETIONARY else null,
        conversion: ReportingConversion? = null,
        refundOf: String? = null,
        transferId: String? = null,
    ) = Transaction(
        id = TransactionId(id),
        accountId = account.id,
        type = type,
        amount = amount,
        date = date,
        reportingConversion = conversion ?: CurrencyConverter.toReporting(amount, if (amount.currency == Currencies.USD) usdRate else null),
        spendingClass = spendingClass,
        refundOf = refundOf?.let(::TransactionId),
        transferId = transferId?.let(::TransferId),
        createdAt = T0,
    )

    fun correction(id: String, txId: String, changes: TransactionChanges, at: Instant = T0.plusSeconds(60)) =
        Correction(CorrectionId(id), TransactionId(txId), changes, at)

    fun void(txId: String) = VoidMarker(TransactionId(txId), T0.plusSeconds(120))

    fun entry(tx: Transaction, voided: Boolean = false) =
        LedgerEntry(tx, tx, emptyList(), if (voided) void(tx.id.value) else null, emptyList())
}

/** In-memory LedgerRepository with the same write-once rule as Firestore. */
class FakeLedgerRepository : LedgerRepository {
    val transactions = linkedMapOf<TransactionId, Transaction>()
    val corrections = mutableListOf<Correction>()
    val voids = linkedMapOf<TransactionId, VoidMarker>()
    val links = mutableListOf<EvidenceLink>()
    private val ledger = MutableStateFlow<List<LedgerEntry>>(emptyList())

    /** Saves a test has marked as refused by the server; retrying one removes it and records the call. */
    val rejectedWrites = MutableStateFlow<List<RejectedLedgerWrite>>(emptyList())
    val retriedWrites = mutableListOf<String>()

    override fun observeLedger(): Flow<List<LedgerEntry>> = ledger

    override fun observeRejectedWrites(): Flow<List<RejectedLedgerWrite>> = rejectedWrites

    override suspend fun retryRejectedWrite(id: String) {
        retriedWrites += id
        rejectedWrites.value = rejectedWrites.value.filterNot { it.id == id }
    }

    override fun dismissRejectedWrite(id: String) {
        rejectedWrites.value = rejectedWrites.value.filterNot { it.id == id }
    }

    /** Re-projects the stored records so observers see every save, correction and undo. */
    private fun publish() {
        ledger.value = LedgerProjector.project(transactions.values, corrections, voids.values, links)
    }

    override suspend fun saveTransaction(transaction: Transaction) {
        transactions.putIfAbsent(transaction.id, transaction)
        publish()
    }

    override suspend fun saveTransfer(transfer: Transfer) {
        val records = listOf(transfer.outLeg(), transfer.inLeg()) + listOfNotNull(transfer.fee)
        if (records.none { it.id in transactions }) records.forEach { transactions[it.id] = it }
        publish()
    }

    override suspend fun addCorrection(correction: Correction) {
        if (corrections.none { it.id == correction.id }) corrections += correction
        publish()
    }

    override suspend fun void(markers: List<VoidMarker>) {
        markers.forEach { voids.putIfAbsent(it.transactionId, it) }
        publish()
    }

    override suspend fun linkEvidence(link: EvidenceLink) {
        if (links.none { it.evidenceId == link.evidenceId }) links += link
        publish()
    }
}

class FakeAccountRepository(vararg initial: com.example.zar.core.domain.model.Account) : com.example.zar.core.domain.repository.AccountRepository {
    private val accounts = MutableStateFlow(initial.toList())
    override fun observeAccounts(): Flow<List<com.example.zar.core.domain.model.Account>> = accounts
    override suspend fun saveAccount(account: com.example.zar.core.domain.model.Account) {
        accounts.value = accounts.value.filterNot { it.id == account.id } + account
    }
}
