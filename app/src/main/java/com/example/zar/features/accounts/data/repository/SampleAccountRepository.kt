package com.example.zar.features.accounts.data.repository

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.repository.AccountRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Temporary fixed accounts so income and other features can be built before D03.
 * Replace with the real Firestore implementation and update the binding in RepositoryModule.
 */
@Singleton
class SampleAccountRepository @Inject constructor() : AccountRepository {

    private val accounts = MutableStateFlow(
        listOf(
            Account(AccountId("sample-bank"), "Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, START),
            Account(AccountId("sample-cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Unknown, START),
            Account(AccountId("sample-payoneer"), "Payoneer", AccountType.FREELANCE, Currencies.USD, OpeningBalance.Unknown, START),
        ),
    )

    override fun observeAccounts(): Flow<List<Account>> = accounts

    override suspend fun saveAccount(account: Account) {
        accounts.update { list -> list.filterNot { it.id == account.id } + account }
    }

    private companion object {
        val START: LocalDate = LocalDate.of(2026, 10, 1)
    }
}
