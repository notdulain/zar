package com.example.zar.core.domain.repository

import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionId
import kotlinx.coroutines.flow.Flow

/**
 * Expected money (freelance milestones, bill occurrences). Kept apart from the ledger so it can
 * never leak into Received or Spent. Expectations are cancelled, not deleted.
 */
interface ExpectedPaymentRepository {
    fun observeExpectedPayments(): Flow<List<ExpectedPayment>>
    fun observeMatches(): Flow<List<PaymentMatch>>
    suspend fun saveExpectedPayment(payment: ExpectedPayment)
    suspend fun saveMatch(match: PaymentMatch)
    suspend fun removeMatch(expectedPaymentId: ExpectedPaymentId, transactionId: TransactionId)
}
