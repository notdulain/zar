package com.example.zar.core.data.remote.firestore

import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedPaymentStatus
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionId
import java.time.Instant
import java.time.LocalDate

/**
 * Converts expected payments and payment matches to and from Firestore maps, using the field
 * names in firestore.rules. Readers return null for malformed documents.
 */
object ExpectedPaymentDocumentMapper {

    fun expectedPaymentToMap(payment: ExpectedPayment): Map<String, Any> = buildMap {
        put("id", payment.id.value)
        put("direction", payment.direction.name)
        put("amount", LedgerDocumentMapper.moneyToMap(payment.amount))
        put("dueDate", payment.dueDate.toString())
        put("title", payment.title)
        put("source", sourceToMap(payment.source))
        payment.accountId?.let { put("accountId", it.value) }
        put("status", payment.status.name)
        put("createdAt", payment.createdAt.toEpochMilli())
    }

    fun expectedPaymentFromMap(map: Map<String, Any?>): ExpectedPayment? = runCatching {
        ExpectedPayment(
            id = ExpectedPaymentId(map["id"] as String),
            direction = Direction.valueOf(map["direction"] as String),
            amount = LedgerDocumentMapper.moneyFromMap(map["amount"] as Map<*, *>)!!,
            dueDate = LocalDate.parse(map["dueDate"] as String),
            title = map["title"] as String,
            source = sourceFromMap(map["source"] as Map<*, *>)!!,
            accountId = (map["accountId"] as String?)?.let(::AccountId),
            status = ExpectedPaymentStatus.valueOf(map["status"] as String),
            createdAt = Instant.ofEpochMilli((map["createdAt"] as Number).toLong()),
        )
    }.getOrNull()

    fun matchToMap(match: PaymentMatch): Map<String, Any> = mapOf(
        "expectedPaymentId" to match.expectedPaymentId.value,
        "transactionId" to match.transactionId.value,
        "amount" to LedgerDocumentMapper.moneyToMap(match.amount),
        "matchedAt" to match.matchedAt.toEpochMilli(),
    )

    fun matchFromMap(map: Map<String, Any?>): PaymentMatch? = runCatching {
        PaymentMatch(
            expectedPaymentId = ExpectedPaymentId(map["expectedPaymentId"] as String),
            transactionId = TransactionId(map["transactionId"] as String),
            amount = LedgerDocumentMapper.moneyFromMap(map["amount"] as Map<*, *>)!!,
            matchedAt = Instant.ofEpochMilli((map["matchedAt"] as Number).toLong()),
        )
    }.getOrNull()

    private fun sourceToMap(source: ExpectedSource): Map<String, Any> = when (source) {
        is ExpectedSource.FreelanceMilestone -> mapOf(
            "type" to "FREELANCE_MILESTONE",
            "projectId" to source.projectId,
            "milestoneId" to source.milestoneId,
        )
        is ExpectedSource.BillOccurrence -> mapOf(
            "type" to "BILL_OCCURRENCE",
            "billId" to source.billId,
            "occurrenceDate" to source.occurrenceDate.toString(),
        )
        ExpectedSource.QuickNote -> mapOf("type" to "QUICK_NOTE")
        ExpectedSource.Manual -> mapOf("type" to "MANUAL")
    }

    private fun sourceFromMap(map: Map<*, *>): ExpectedSource? = when (map["type"]) {
        "FREELANCE_MILESTONE" -> ExpectedSource.FreelanceMilestone(map["projectId"] as String, map["milestoneId"] as String)
        "BILL_OCCURRENCE" -> ExpectedSource.BillOccurrence(map["billId"] as String, LocalDate.parse(map["occurrenceDate"] as String))
        "QUICK_NOTE" -> ExpectedSource.QuickNote
        "MANUAL" -> ExpectedSource.Manual
        else -> null
    }
}
