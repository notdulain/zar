package com.example.zar.features.crypto.domain.repository

import com.example.zar.core.domain.model.AccountId
import com.example.zar.features.crypto.domain.model.OpeningCost
import kotlinx.coroutines.flow.Flow

/** Optional LKR cost of the coins each crypto wallet started with, for the signed-in user. */
interface OpeningCostRepository {
    fun observeOpeningCosts(): Flow<Map<AccountId, OpeningCost>>

    suspend fun saveOpeningCost(cost: OpeningCost)

    suspend fun clearOpeningCost(accountId: AccountId)
}
