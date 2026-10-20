package com.example.zar.core.domain.model

import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.ReportingConversion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Money moved between two owned accounts. Its two [Transaction] legs and optional fee share one atomic write;
 * [received] may differ from [sent] when currencies differ. Fees are a separate expense.
 */
data class Transfer(
    val id: TransferId,
    val fromAccountId: AccountId,
    val toAccountId: AccountId,
    val sent: Money,
    val received: Money,
    val date: LocalDate,
    val sentConversion: ReportingConversion,
    val receivedConversion: ReportingConversion,
    val note: String? = null,
    val source: RecordSource = RecordSource.MANUAL,
    val createdAt: Instant,
    /** Separate expense stored atomically with the legs when the transfer has a fee. */
    val fee: Transaction? = null,
    /** User-selected local time shared by both legs and the fee. */
    val time: LocalTime? = null,
) {
    init {
        require(fromAccountId != toAccountId) { "A transfer needs two different accounts" }
        require(sent.isPositive && received.isPositive) { "Transfer amounts must be positive" }
        fee?.let {
            require(it.type == TransactionType.EXPENSE && it.accountId == fromAccountId) { "A transfer fee must be an expense on the sending account" }
            require(it.amount.currency == sent.currency) { "A transfer fee must use the sending currency" }
            require(it.id != outLegId && it.id != inLegId) { "A fee cannot replace a transfer leg" }
        }
    }

    val outLegId: TransactionId get() = id.outLegId()
    val inLegId: TransactionId get() = id.inLegId()

    fun outLeg() = Transaction(
        id = outLegId, accountId = fromAccountId, type = TransactionType.TRANSFER_OUT, amount = sent,
        date = date, reportingConversion = sentConversion, note = note, source = source,
        transferId = id, createdAt = createdAt, time = time,
    )

    fun inLeg() = Transaction(
        id = inLegId, accountId = toAccountId, type = TransactionType.TRANSFER_IN, amount = received,
        date = date, reportingConversion = receivedConversion, note = note, source = source,
        transferId = id, createdAt = createdAt, time = time,
    )
}

fun TransferId.outLegId() = TransactionId("$value-out")
fun TransferId.inLegId() = TransactionId("$value-in")
