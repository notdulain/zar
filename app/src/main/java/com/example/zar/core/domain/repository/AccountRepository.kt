package com.example.zar.core.domain.repository

import com.example.zar.core.domain.model.Account
import kotlinx.coroutines.flow.Flow

/** Accounts are editable and archived rather than deleted. Implemented in D03. */
interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>
    suspend fun saveAccount(account: Account)
}
