package com.example.zar.features.bills.domain.repository

import com.example.zar.features.bills.domain.model.Bill
import kotlinx.coroutines.flow.Flow

/** The signed-in user's bill schedules. Bills are stopped, never deleted. */
interface BillRepository {
    fun observeBills(): Flow<List<Bill>>

    /** Creates or updates. Returns once stored locally (offline first). */
    suspend fun saveBill(bill: Bill)
}
