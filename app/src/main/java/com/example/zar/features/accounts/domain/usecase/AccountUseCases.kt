package com.example.zar.features.accounts.domain.usecase

import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.resultOf
import com.example.zar.features.accounts.domain.AccountRules
import com.example.zar.features.accounts.domain.model.AccountDetail
import com.example.zar.features.accounts.domain.model.AccountDetails
import com.example.zar.features.accounts.domain.model.AccountsOverview
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val accountOrder = compareBy<Account>({ AccountRules.TYPE_ORDER.indexOf(it.type) }, { it.name.lowercase() })

/** Every account with its balance, plus the total of what is known. */
class ObserveAccountsOverviewUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val ledger: LedgerRepository,
) {
    operator fun invoke(): Flow<AccountsOverview> = combine(accounts.observeAccounts(), ledger.observeLedger()) { list, entries ->
        val balances = list.sortedWith(accountOrder).map { AccountBalanceCalculator.calculate(it, entries, ZarTime.today()) }
        val (archived, active) = balances.partition { it.account.isArchived }

        // Only reporting-currency balances can be added up until exchange rates exist.
        val counted = active.filter { it.account.currency == Currencies.REPORTING && it.current != null }
        AccountsOverview(
            balances = active,
            archived = archived,
            total = counted.map { it.current!! }.reduceOrNull<Money, Money> { sum, next -> sum + next },
            countedAccounts = counted.size,
            otherCurrencyAccounts = active.count { it.account.currency != Currencies.REPORTING },
        )
    }
}

/** One account with its balance and its latest entries; null if it doesn't exist. */
class ObserveAccountDetailUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val ledger: LedgerRepository,
) {
    operator fun invoke(id: AccountId, recentCount: Int = RECENT_COUNT): Flow<AccountDetail?> =
        combine(accounts.observeAccounts(), ledger.observeLedger()) { list, entries ->
            val account = list.firstOrNull { it.id == id } ?: return@combine null
            val balance: AccountBalance = AccountBalanceCalculator.calculate(account, entries, ZarTime.today())
            val recent = entries.active()
                .filter { it.current.accountId == id }
                .sortedWith(compareByDescending<LedgerEntry> { it.current.date }.thenByDescending { it.current.createdAt })
                .take(recentCount)
            AccountDetail(balance, recent)
        }

    private companion object {
        const val RECENT_COUNT = 3
    }
}

/** One account for the edit form, or null if it doesn't exist. */
class ObserveAccountUseCase @Inject constructor(
    private val accounts: AccountRepository,
) {
    operator fun invoke(id: AccountId): Flow<Account?> = accounts.observeAccounts().map { list -> list.firstOrNull { it.id == id } }
}

/** Accounts new entries can be recorded into. */
class ObserveActiveAccountsUseCase @Inject constructor(
    private val accounts: AccountRepository,
) {
    operator fun invoke(): Flow<List<Account>> =
        accounts.observeAccounts().map { list -> list.filterNot { it.isArchived }.sortedWith(accountOrder) }
}

/**
 * Adds an account, or saves an edit when [existing] is given. Reuse the same [id] when retrying
 * a new account so the save can't create a second one. Opening funds are stored on the account;
 * no transaction is written, so they never count as received income.
 */
class SaveAccountUseCase @Inject constructor(
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(id: AccountId, details: AccountDetails, existing: Account? = null): Result<Unit> {
        val account = try {
            if (existing == null) AccountRules.newAccount(id, details) else AccountRules.edited(existing, details)
        } catch (e: IllegalArgumentException) {
            return Result.failure(LedgerError.InvalidRecord(e.message ?: "Invalid account"))
        }
        return resultOf { accounts.saveAccount(account) }
    }
}

/** Accounts are archived rather than deleted, so their history stays readable. */
class SetAccountArchivedUseCase @Inject constructor(
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(account: Account, archived: Boolean): Result<Unit> {
        if (account.isArchived == archived) return Result.success(Unit)
        return resultOf { accounts.saveAccount(account.copy(isArchived = archived)) }
    }
}
